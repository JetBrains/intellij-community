// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.impl;

import com.intellij.conversion.*;
import com.intellij.conversion.impl.ConversionContextImpl;
import com.intellij.conversion.impl.ConversionRunner;
import com.intellij.conversion.impl.ProjectConversionUtil;
import com.intellij.conversion.impl.ui.ConvertProjectDialog;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.graph.*;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import com.intellij.util.xmlb.annotations.XMap;
import gnu.trove.THashMap;
import org.jdom.Document;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * @author nik
 */
public final class ConversionServiceImpl extends ConversionService {
  private static final Logger LOG = Logger.getInstance(ConversionServiceImpl.class);

  @NotNull
  @Override
  public ConversionResult convertSilently(@NotNull Path projectPath, @NotNull ConversionListener listener) {
    try {
      if (!isConversionNeeded(projectPath)) {
        return ConversionResultImpl.CONVERSION_NOT_NEEDED;
      }

      listener.conversionNeeded();
      ConversionContextImpl context = new ConversionContextImpl(projectPath);
      final List<ConversionRunner> runners = getConversionRunners(context);

      Set<Path> affectedFiles = new HashSet<>();
      for (ConversionRunner runner : runners) {
        affectedFiles.addAll(runner.getAffectedFiles());
      }

      final List<Path> readOnlyFiles = ConversionRunner.getReadOnlyFiles(affectedFiles);
      if (!readOnlyFiles.isEmpty()) {
        listener.cannotWriteToFiles(ContainerUtil.map(readOnlyFiles, path -> path.toFile()));
        return ConversionResultImpl.ERROR_OCCURRED;
      }
      final File backupDir = ProjectConversionUtil.backupFiles(affectedFiles, context.getProjectBaseDir());
      List<ConversionRunner> usedRunners = new ArrayList<>();
      for (ConversionRunner runner : runners) {
        if (runner.isConversionNeeded()) {
          runner.preProcess();
          runner.process();
          runner.postProcess();
          usedRunners.add(runner);
        }
      }
      context.saveFiles(affectedFiles, usedRunners);
      listener.successfullyConverted(backupDir);
      saveConversionResult(context);
      return new ConversionResultImpl(runners);
    }
    catch (CannotConvertException | IOException e) {
      listener.error(e.getMessage());
    }
    return ConversionResultImpl.ERROR_OCCURRED;
  }

  @NotNull
  @Override
  public ConversionResult convert(@NotNull Path projectPath) throws CannotConvertException {
    if (!ConverterProvider.EP_NAME.hasAnyExtensions() || !Files.exists(projectPath) || ApplicationManager.getApplication().isHeadlessEnvironment()) {
      return ConversionResultImpl.CONVERSION_NOT_NEEDED;
    }

    final ConversionContextImpl context = new ConversionContextImpl(projectPath);
    if (!isConversionNeeded(context)) {
      return ConversionResultImpl.CONVERSION_NOT_NEEDED;
    }

    List<ConversionRunner> converters = getConversionRunners(context);
    Ref<ConversionResult> ref = new Ref<>(ConversionResultImpl.CONVERSION_CANCELED);
    ApplicationManager.getApplication().invokeAndWait(() -> {
      ConvertProjectDialog dialog = new ConvertProjectDialog(context, converters);
      dialog.show();
      if (dialog.isConverted()) {
        saveConversionResult(context);
        ref.set(new ConversionResultImpl(converters));
      }
    });
    return ref.get();
  }

  private static List<ConversionRunner> getConversionRunners(ConversionContextImpl context) throws CannotConvertException {
    final List<ConversionRunner> converters = getSortedConverters(context);
    final Iterator<ConversionRunner> iterator = converters.iterator();

    Set<String> convertersToRunIds = new HashSet<>();
    while (iterator.hasNext()) {
      ConversionRunner runner = iterator.next();
      boolean conversionNeeded = runner.isConversionNeeded();
      if (!conversionNeeded) {
        for (String id : runner.getProvider().getPrecedingConverterIds()) {
          if (convertersToRunIds.contains(id)) {
            conversionNeeded = true;
            break;
          }
        }
      }

      if (conversionNeeded) {
        convertersToRunIds.add(runner.getProvider().getId());
      }
      else {
        iterator.remove();
      }
    }
    return converters;
  }

  private static boolean isConversionNeeded(@NotNull Path projectPath) throws CannotConvertException {
    return isConversionNeeded(new ConversionContextImpl(projectPath));
  }

  private static boolean isConversionNeeded(@NotNull ConversionContextImpl context ) {
    try {
      final List<ConversionRunner> runners = getSortedConverters(context);
      if (runners.isEmpty()) {
        return false;
      }
      for (ConversionRunner runner : runners) {
        if (runner.isConversionNeeded()) {
          return true;
        }
      }
      saveConversionResult(context);
    }
    catch (CannotConvertException e) {
      LOG.info("Cannot check whether conversion of project files is needed or not, conversion won't be performed", e);
    }
    return false;
  }

  private static List<ConversionRunner> getSortedConverters(@NotNull ConversionContextImpl context) {
    final CachedConversionResult conversionResult = loadCachedConversionResult(context.getProjectFile());
    final Map<String, Long> oldMap = conversionResult.myProjectFilesTimestamps;
    Map<String, Long> newMap = getProjectFilesMap(context);
    boolean changed = false;
    LOG.debug("Checking project files");
    for (Map.Entry<String, Long> entry : newMap.entrySet()) {
      final String path = entry.getKey();
      final Long oldValue = oldMap.get(path);
      if (oldValue == null) {
        LOG.debug(" new file: " + path);
        changed = true;
      }
      else if (!entry.getValue().equals(oldValue)) {
        LOG.debug(" changed file: " + path);
        changed = true;
      }
    }

    final Set<String> performedConversionIds;
    if (changed) {
      performedConversionIds = Collections.emptySet();
      LOG.debug("Project files were modified.");
    }
    else {
      performedConversionIds = conversionResult.myAppliedConverters;
      LOG.debug("Project files are up to date. Applied converters: " + performedConversionIds);
    }
    return createConversionRunners(context, performedConversionIds);
  }

  @NotNull
  private static Map<String, Long> getProjectFilesMap(@NotNull ConversionContextImpl context) {
    Map<String, Long> map = new THashMap<>();
    for (File file : context.getAllProjectFiles()) {
      if (file.exists()) {
        map.put(file.getAbsolutePath(), file.lastModified());
      }
    }
    return map;
  }

  private static List<ConversionRunner> createConversionRunners(ConversionContextImpl context, final Set<String> performedConversionIds) {
    List<ConversionRunner> runners = new ArrayList<>();
    final ConverterProvider[] providers = ConverterProvider.EP_NAME.getExtensions();
    for (ConverterProvider provider : providers) {
      if (!performedConversionIds.contains(provider.getId())) {
        runners.add(new ConversionRunner(provider, context));
      }
    }
    final Graph<ConverterProvider> graph = GraphGenerator.generate(CachingSemiGraph.cache(new ConverterProvidersGraph(providers)));
    final DFSTBuilder<ConverterProvider> builder = new DFSTBuilder<>(graph);
    if (!builder.isAcyclic()) {
      final Pair<ConverterProvider,ConverterProvider> pair = builder.getCircularDependency();
      LOG.error("cyclic dependencies between converters: " + pair.getFirst().getId() + " and " + pair.getSecond().getId());
    }
    final Comparator<ConverterProvider> comparator = builder.comparator();
    Collections.sort(runners, (o1, o2) -> comparator.compare(o1.getProvider(), o2.getProvider()));
    return runners;
  }

  @Override
  public void saveConversionResult(@NotNull Path projectPath) {
    try {
      saveConversionResult(new ConversionContextImpl(projectPath));
    }
    catch (CannotConvertException e) {
      LOG.info(e);
    }
  }

  private static void saveConversionResult(ConversionContextImpl context) {
    CachedConversionResult conversionResult = new CachedConversionResult();
    for (ConverterProvider provider : ConverterProvider.EP_NAME.getExtensions()) {
      conversionResult.myAppliedConverters.add(provider.getId());
    }
    conversionResult.myProjectFilesTimestamps = getProjectFilesMap(context);
    final File infoFile = getConversionInfoFile(context.getProjectFile());
    FileUtil.createParentDirs(infoFile);
    try {
      JDOMUtil.writeDocument(new Document(XmlSerializer.serialize(conversionResult)), infoFile, SystemProperties.getLineSeparator());
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }

  @NotNull
  private static CachedConversionResult loadCachedConversionResult(@NotNull File projectFile) {
    try {
      final File infoFile = getConversionInfoFile(projectFile);
      if (!infoFile.exists()) {
        return new CachedConversionResult();
      }
      return XmlSerializer.deserialize(JDOMUtil.load(infoFile), CachedConversionResult.class);
    }
    catch (Exception e) {
      LOG.info(e);
      return new CachedConversionResult();
    }
  }

  private static File getConversionInfoFile(@NotNull File projectFile) {
    String dirName = PathUtil.suggestFileName(projectFile.getName() + Integer.toHexString(projectFile.getAbsolutePath().hashCode()));
    return new File(PathManager.getSystemPath() + File.separator + "conversion" + File.separator + dirName + ".xml");
  }

  @Override
  @NotNull
  public ConversionResult convertModule(@NotNull final Project project, @NotNull final Path moduleFile) {
    final String url = project.getPresentableUrl();
    assert url != null : project;
    final Path projectPath = Paths.get(url);

    if (!isConversionNeeded(projectPath, moduleFile)) {
      return ConversionResultImpl.CONVERSION_NOT_NEEDED;
    }

    final int res = Messages.showYesNoDialog(project, IdeBundle.message("message.module.file.has.an.older.format.do.you.want.to.convert.it"),
                                             IdeBundle.message("dialog.title.convert.module"), Messages.getQuestionIcon());
    if (res != Messages.YES) {
      return ConversionResultImpl.CONVERSION_CANCELED;
    }
    if (!Files.isWritable(moduleFile)) {
      Messages.showErrorDialog(project, IdeBundle.message("error.message.cannot.modify.file.0", moduleFile.toAbsolutePath().toString()),
                               IdeBundle.message("dialog.title.convert.module"));
      return ConversionResultImpl.ERROR_OCCURRED;
    }

    try {
      ConversionContextImpl context = new ConversionContextImpl(projectPath);
      final List<ConversionRunner> runners = createConversionRunners(context, Collections.emptySet());
      final File backupFile = ProjectConversionUtil.backupFile(moduleFile);
      List<ConversionRunner> usedRunners = new ArrayList<>();
      for (ConversionRunner runner : runners) {
        if (runner.isModuleConversionNeeded(moduleFile)) {
          runner.convertModule(moduleFile);
          usedRunners.add(runner);
        }
      }
      context.saveFiles(Collections.singletonList(moduleFile), usedRunners);
      Messages.showInfoMessage(project, IdeBundle.message("message.your.module.was.successfully.converted.br.old.version.was.saved.to.0", backupFile.getAbsolutePath()),
                               IdeBundle.message("dialog.title.convert.module"));
      return new ConversionResultImpl(runners);
    }
    catch (CannotConvertException e) {
      LOG.info(e);
      Messages.showErrorDialog(IdeBundle.message("error.cannot.load.project", e.getMessage()), "Cannot Convert Module");
      return ConversionResultImpl.ERROR_OCCURRED;
    }
    catch (IOException e) {
      LOG.info(e);
      return ConversionResultImpl.ERROR_OCCURRED;
    }
  }

  private static boolean isConversionNeeded(Path projectPath, Path moduleFile) {
    try {
      ConversionContextImpl context = new ConversionContextImpl(projectPath);
      final List<ConversionRunner> runners = createConversionRunners(context, Collections.emptySet());
      for (ConversionRunner runner : runners) {
        if (runner.isModuleConversionNeeded(moduleFile)) {
          return true;
        }
      }
      return false;
    }
    catch (CannotConvertException e) {
      LOG.info(e);
      return false;
    }
  }

  @Tag("conversion")
  public static class CachedConversionResult {
    @Tag("applied-converters")
    @XCollection(elementName = "converter", valueAttributeName = "id")
    public Set<String> myAppliedConverters = new HashSet<>();

    @XMap(propertyElementName = "project-files", entryTagName = "file", keyAttributeName = "path", valueAttributeName = "timestamp")
    public Map<String, Long> myProjectFilesTimestamps = new HashMap<>();
  }

  private static class ConverterProvidersGraph implements InboundSemiGraph<ConverterProvider> {
    private final ConverterProvider[] myProviders;

    ConverterProvidersGraph(ConverterProvider[] providers) {
      myProviders = providers;
    }

    @NotNull
    @Override
    public Collection<ConverterProvider> getNodes() {
      return Arrays.asList(myProviders);
    }

    @NotNull
    @Override
    public Iterator<ConverterProvider> getIn(ConverterProvider n) {
      List<ConverterProvider> preceding = new ArrayList<>();
      for (String id : n.getPrecedingConverterIds()) {
        for (ConverterProvider provider : myProviders) {
          if (provider.getId().equals(id)) {
            preceding.add(provider);
          }
        }
      }
      return preceding.iterator();
    }
  }
}
