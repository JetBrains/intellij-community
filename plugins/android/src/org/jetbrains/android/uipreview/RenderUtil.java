package org.jetbrains.android.uipreview;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.RenderResources;
import com.android.ide.common.rendering.api.RenderSession;
import com.android.ide.common.rendering.api.Result;
import com.android.ide.common.resources.ResourceDeltaKind;
import com.android.ide.common.resources.ResourceFolder;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ScanningContext;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.io.IAbstractFile;
import com.android.io.IAbstractFolder;
import com.android.io.IAbstractResource;
import com.android.io.StreamException;
import com.android.sdklib.IAndroidTarget;
import com.intellij.compiler.impl.javaCompiler.javac.JavacSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.dom.manifest.Application;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.android.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xmlpull.v1.XmlPullParserException;

import javax.imageio.ImageIO;
import java.io.*;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class RenderUtil {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.uipreview.RenderUtil");

  private static final String DEFAULT_APP_LABEL = "Android application";

  private RenderUtil() {
  }

  @Nullable
  public static RenderSession createRenderSession(@NotNull Project project,
                                                  @NotNull String layoutXmlText,
                                                  @Nullable VirtualFile layoutXmlFile,
                                                  @NotNull IAndroidTarget target,
                                                  @NotNull AndroidFacet facet,
                                                  @NotNull FolderConfiguration config,
                                                  float xdpi,
                                                  float ydpi,
                                                  @NotNull ThemeData theme)
    throws RenderingException, IOException, AndroidSdkNotConfiguredException {
    final Sdk sdk = ModuleRootManager.getInstance(facet.getModule()).getSdk();
    if (sdk == null || !(sdk.getSdkType() instanceof AndroidSdkType)) {
      throw new AndroidSdkNotConfiguredException();
    }

    final AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
    if (data == null) {
      throw new AndroidSdkNotConfiguredException();
    }

    final AndroidPlatform platform = data.getAndroidPlatform();
    if (platform == null) {
      throw new AndroidSdkNotConfiguredException();
    }

    config.setVersionQualifier(new VersionQualifier(target.getVersion().getApiLevel()));

    final RenderServiceFactory factory = platform.getSdkData().getTargetData(target).getRenderServiceFactory(project);
    if (factory == null) {
      throw new RenderingException(AndroidBundle.message("android.layout.preview.cannot.load.library.error"));
    }
    final List<AndroidFacet> allLibraries = AndroidUtils.getAllAndroidDependencies(facet.getModule(), true);
    final List<ProjectResources> libResources = new ArrayList<ProjectResources>();
    final List<ProjectResources> emptyResList = Collections.emptyList();

    for (AndroidFacet libFacet : allLibraries) {
      if (!libFacet.equals(facet)) {
        libResources.add(loadProjectResources(libFacet, null, null, emptyResList));
      }
    }
    final ProjectResources projectResources = loadProjectResources(facet, layoutXmlText, layoutXmlFile, libResources);

    final VirtualFile[] resourceDirs = facet.getLocalResourceManager().getAllResourceDirs();
    final IAbstractFolder[] resFolders = toAbstractFolders(resourceDirs);

    loadResources(projectResources, layoutXmlText, layoutXmlFile, resFolders);
    final int minSdkVersion = getMinSdkVersion(facet);

    final ProjectCallback callback = new ProjectCallback(factory.getLibrary(), facet.getModule(), projectResources);
    try {
      callback.loadAndParseRClass();
    }
    catch (ClassNotFoundException e) {
      LOG.debug(e);
    }
    catch (IncompatibleClassFileFormatException e) {
      LOG.debug(e);
    }

    final Pair<RenderResources, RenderResources> pair =
      factory.createResourceResolver(facet, config, projectResources, theme.getName(), theme.isProjectTheme());
    final RenderService renderService = factory.createService(pair.getFirst(), pair.getSecond(), config, xdpi, ydpi, callback, minSdkVersion);

    try {
      return renderService.createRenderSession(layoutXmlText, getAppLabelToShow(facet));
    }
    catch (XmlPullParserException e) {
      throw new RenderingException(e);
    }
  }

  @Nullable
  public static RenderingResult renderLayout(@NotNull final Module module,
                                             @NotNull String layoutXmlText,
                                             @Nullable VirtualFile layoutXmlFile,
                                             @NotNull String imgPath,
                                             @NotNull IAndroidTarget target,
                                             @NotNull AndroidFacet facet,
                                             @NotNull FolderConfiguration config,
                                             float xdpi,
                                             float ydpi,
                                             @NotNull ThemeData theme)
    throws RenderingException, IOException, AndroidSdkNotConfiguredException {
    final Project project = module.getProject();

    final Sdk sdk = ModuleRootManager.getInstance(facet.getModule()).getSdk();
    if (sdk == null || !(sdk.getSdkType() instanceof AndroidSdkType)) {
      throw new AndroidSdkNotConfiguredException();
    }

    final AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
    if (data == null) {
      throw new AndroidSdkNotConfiguredException();
    }

    final AndroidPlatform platform = data.getAndroidPlatform();
    if (platform == null) {
      throw new AndroidSdkNotConfiguredException();
    }

    config.setVersionQualifier(new VersionQualifier(target.getVersion().getApiLevel()));

    final RenderServiceFactory factory = platform.getSdkData().getTargetData(target).getRenderServiceFactory(project);
    if (factory == null) {
      throw new RenderingException(AndroidBundle.message("android.layout.preview.cannot.load.library.error"));
    }

    final List<AndroidFacet> allLibraries = AndroidUtils.getAllAndroidDependencies(module, true);
    final List<ProjectResources> libResources = new ArrayList<ProjectResources>();
    final List<ProjectResources> emptyResList = Collections.emptyList();

    for (AndroidFacet libFacet : allLibraries) {
      if (!libFacet.equals(facet)) {
        libResources.add(loadProjectResources(libFacet, null, null, emptyResList));
      }
    }
    final ProjectResources projectResources = loadProjectResources(facet, layoutXmlText, layoutXmlFile, libResources);

    final int minSdkVersion = getMinSdkVersion(facet);
    String missingRClassMessage = null;
    boolean missingRClass = false;
    boolean incorrectRClassFormat = false;
    String rClassName = null;

    final ProjectCallback callback = new ProjectCallback(factory.getLibrary(), facet.getModule(), projectResources);

    try {
      callback.loadAndParseRClass();
    }
    catch (ClassNotFoundException e) {
      LOG.debug(e);
      missingRClassMessage = e.getMessage();
      missingRClass = true;
    }
    catch (IncompatibleClassFileFormatException e) {
      LOG.debug(e);
      incorrectRClassFormat = true;
      rClassName = e.getClassName();
    }

    final Pair<RenderResources, RenderResources> pair =
      factory.createResourceResolver(facet, config, projectResources, theme.getName(), theme.isProjectTheme());
    final RenderService renderService = factory.createService(pair.getFirst(), pair.getSecond(), config, xdpi, ydpi, callback, minSdkVersion);

    final RenderSession session;
    try {
      session = renderService.createRenderSession(layoutXmlText, getAppLabelToShow(facet));
    }
    catch (XmlPullParserException e) {
      throw new RenderingException(e);
    }
    if (session == null) {
      return null;
    }

    final List<FixableIssueMessage> warnMessages = new ArrayList<FixableIssueMessage>();

    if (callback.hasUnsupportedClassVersionProblem() || (incorrectRClassFormat && callback.hasLoadedClasses())) {
      reportIncorrectClassFormatWarning(callback, rClassName, incorrectRClassFormat, warnMessages);
    }

    if (missingRClass && callback.hasLoadedClasses()) {
      final StringBuilder builder = new StringBuilder();
      builder.append(missingRClassMessage != null && missingRClassMessage.length() > 0
                     ? ("Class not found error: " + missingRClassMessage + ".")
                     : "R class not found.")
        .append(" Try to build project");
      warnMessages.add(new FixableIssueMessage(builder.toString()));
    }

    reportMissingClassesWarning(warnMessages, callback.getMissingClasses());

    reportBrokenClassesWarning(warnMessages, callback.getBrokenClasses());

    final Result result = session.getResult();
    if (!result.isSuccess()) {
      final Throwable exception = result.getException();

      if (exception != null) {
        final List<Throwable> exceptionsFromWarnings = getNonNullValues(callback.getBrokenClasses());

        if (exceptionsFromWarnings.size() > 0 &&
            exception instanceof ClassCastException &&
            (SdkConstants.CLASS_MOCK_VIEW + " cannot be cast to " + SdkConstants.CLASS_VIEWGROUP)
              .equalsIgnoreCase(exception.getMessage())) {
          throw new RenderingException(exceptionsFromWarnings.toArray(new Throwable[exceptionsFromWarnings.size()]));
        }
        throw new RenderingException(exception);
      }
      final String message = result.getErrorMessage();
      if (message != null) {
        LOG.info(message);
        throw new RenderingException();
      }
      return null;
    }

    final String format = FileUtil.getExtension(imgPath);
    ImageIO.write(session.getImage(), format, new File(imgPath));

      session.dispose();

    return new RenderingResult(warnMessages);
  }

  private static void reportBrokenClassesWarning(@NotNull List<FixableIssueMessage> warnMessages,
                                                 @NotNull Map<String, Throwable> brokenClasses) {
    if (brokenClasses.size() > 0) {
      final StringBuilder builder = new StringBuilder();
      if (brokenClasses.size() > 1) {
        builder.append("Unable to initialize:\n");
        for (String brokenClass : brokenClasses.keySet()) {
          builder.append("    ").append(brokenClass).append('\n');
        }
      }
      else {
        builder.append("Unable to initialize ").append(brokenClasses.keySet().iterator().next());
      }
      removeLastNewLineChar(builder);
      warnMessages.add(new FixableIssueMessage(builder.toString()));
    }
  }

  private static void reportMissingClassesWarning(@NotNull List<FixableIssueMessage> warnMessages,
                                                  @NotNull Set<String> missingClasses) {
    if (missingClasses.size() > 0) {
      final StringBuilder builder = new StringBuilder();
      if (missingClasses.size() > 1) {
        builder.append("Missing classes:\n");
        for (String missingClass : missingClasses) {
          builder.append("&nbsp; &nbsp; &nbsp; &nbsp;").append(missingClass).append('\n');
        }
      }
      else {
        builder.append("Missing class ").append(missingClasses.iterator().next());
      }
      removeLastNewLineChar(builder);
      warnMessages.add(new FixableIssueMessage(builder.toString()));
    }
  }

  private static void reportIncorrectClassFormatWarning(@NotNull ProjectCallback callback,
                                                        @Nullable String rClassName,
                                                        boolean incorrectRClassFormat,
                                                        @NotNull List<FixableIssueMessage> warnMessages) {
    final Module module = callback.getModule();
    final Project project = module.getProject();
    final List<Module> problemModules = getProblemModules(module);
    final StringBuilder builder = new StringBuilder("Preview can be incorrect: unsupported classes version");
    final List<Pair<String, Runnable>> quickFixes = new ArrayList<Pair<String, Runnable>>();

    if (problemModules.size() > 0) {
      quickFixes.add(new Pair<String, Runnable>("Rebuild project with '-target 1.6'", new Runnable() {
        @Override
        public void run() {
          final JavacSettings settings = JavacSettings.getInstance(project);
          if (settings.ADDITIONAL_OPTIONS_STRING.length() > 0) {
            settings.ADDITIONAL_OPTIONS_STRING += ' ';
          }
          settings.ADDITIONAL_OPTIONS_STRING += "-target 1.6";
          CompilerManager.getInstance(project).rebuild(null);
        }
      }));

      quickFixes.add(new Pair<String, Runnable>("Change Java SDK to 1.5/1.6", new Runnable() {
        @Override
        public void run() {
          final Set<String> sdkNames = getSdkNamesFromModules(problemModules);

          if (sdkNames.size() == 1) {
            final Sdk sdk = ProjectJdkTable.getInstance().findJdk(sdkNames.iterator().next());

            if (sdk != null && sdk.getSdkType() instanceof AndroidSdkType) {
              final ProjectStructureConfigurable config = ProjectStructureConfigurable.getInstance(project);

              if (ShowSettingsUtil.getInstance().editConfigurable(project, config, new Runnable() {
                public void run() {
                  config.select(sdk, true);
                }
              })) {
                askAndRebuild(project);
              }
              return;
            }
          }

          final String moduleToSelect = problemModules.size() > 0
                                        ? problemModules.iterator().next().getName()
                                        : null;
          if (ModulesConfigurator.showDialog(project, moduleToSelect, ClasspathEditor.NAME)) {
            askAndRebuild(project);
          }
        }
      }));

      final Set<String> classesWithIncorrectFormat = new HashSet<String>(callback.getClassesWithIncorrectFormat());
      if (incorrectRClassFormat && rClassName != null) {
        classesWithIncorrectFormat.add(rClassName);
      }
      if (classesWithIncorrectFormat.size() > 0) {
        quickFixes.add(new Pair<String, Runnable>("Details", new Runnable() {
          @Override
          public void run() {
            showClassesWithIncorrectFormat(project, classesWithIncorrectFormat);
          }
        }));
      }

      builder.append("\nFollowing modules are built with incompatible JDK: ");

      for (Iterator<Module> it = problemModules.iterator(); it.hasNext(); ) {
        Module problemModule = it.next();
        builder.append(problemModule.getName());
        if (it.hasNext()) {
          builder.append(", ");
        }
      }
    }

    warnMessages.add(new FixableIssueMessage(builder.toString(), quickFixes));
  }

  private static void showClassesWithIncorrectFormat(@NotNull Project project, @NotNull Set<String> classesWithIncorrectFormat) {
    final StringBuilder builder = new StringBuilder("Classes with incompatible format:\n");

    for (Iterator<String> it = classesWithIncorrectFormat.iterator(); it.hasNext(); ) {
      builder.append("    ").append(it.next());

      if (it.hasNext()) {
        builder.append('\n');
      }
    }
    Messages.showInfoMessage(project, builder.toString(), "Unsupported class version");
  }

  private static void askAndRebuild(Project project) {
    final int r =
      Messages.showYesNoDialog(project, "You have to rebuild project to see fixed preview. Would you like to do it?",
                               "Rebuild project", Messages.getQuestionIcon());
    if (r == Messages.YES) {
      CompilerManager.getInstance(project).rebuild(null);
    }
  }

  @NotNull
  private static Set<String> getSdkNamesFromModules(@NotNull Collection<Module> modules) {
    final Set<String> result = new HashSet<String>();

    for (Module module : modules) {
      final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();

      if (sdk != null) {
        result.add(sdk.getName());
      }
    }
    return result;
  }

  @NotNull
  private static List<Module> getProblemModules(@NotNull Module root) {
    final List<Module> result = new ArrayList<Module>();
    collectProblemModules(root, new HashSet<Module>(), result);
    return result;
  }

  private static void collectProblemModules(@NotNull Module module, @NotNull Set<Module> visited, @NotNull Collection<Module> result) {
    if (!visited.add(module)) {
      return;
    }

    if (isBuiltByJdk7OrHigher(module)) {
      result.add(module);
    }

    for (Module depModule : ModuleRootManager.getInstance(module).getDependencies(false)) {
      collectProblemModules(depModule, visited, result);
    }
  }

  private static boolean isBuiltByJdk7OrHigher(@NotNull Module module) {
    Sdk sdk = ModuleRootManager.getInstance(module).getSdk();

    if (sdk == null) {
      return false;
    }

    if (sdk.getSdkType() instanceof AndroidSdkType) {
      final AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();

      if (data != null) {
        final Sdk jdk = data.getJavaSdk();

        if (jdk != null) {
          sdk = jdk;
        }
      }
    }
    return sdk.getSdkType() instanceof JavaSdk &&
           JavaSdk.getInstance().isOfVersionOrHigher(sdk, JavaSdkVersion.JDK_1_7);
  }

  private static void removeLastNewLineChar(StringBuilder builder) {
    if (builder.length() > 0 && builder.charAt(builder.length() - 1) == '\n') {
      builder.deleteCharAt(builder.length() - 1);
    }
  }

  @NotNull
  private static <T> List<T> getNonNullValues(@NotNull Map<?, T> map) {
    final List<T> result = new ArrayList<T>();

    for (Map.Entry<?, T> entry : map.entrySet()) {
      final T value = entry.getValue();
      if (value != null) {
        result.add(value);
      }
    }
    return result;
  }

  private static String getAppLabelToShow(final AndroidFacet facet) {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        final Manifest manifest = facet.getManifest();
        if (manifest != null) {
          final Application application = manifest.getApplication();
          if (application != null) {
            final String label = application.getLabel().getStringValue();
            if (label != null) {
              return label;
            }
          }
        }
        return DEFAULT_APP_LABEL;
      }
    });
  }

  private static int getMinSdkVersion(final AndroidFacet facet) {
    final XmlTag manifestTag = ApplicationManager.getApplication().runReadAction(new Computable<XmlTag>() {
      @Nullable
      @Override
      public XmlTag compute() {
        final Manifest manifest = facet.getManifest();
        return manifest != null ? manifest.getXmlTag() : null;
      }
    });
    if (manifestTag != null) {
      for (XmlTag usesSdkTag : manifestTag.findSubTags("uses-sdk")) {
        final int candidate = AndroidUtils.getIntAttrValue(usesSdkTag, "minSdkVersion");
        if (candidate >= 0) {
          return candidate;
        }
      }
    }
    return -1;
  }

  @NotNull
  private static IAbstractFolder[] toAbstractFolders(@NotNull VirtualFile[] folders) {
    final IAbstractFolder[] result = new IAbstractFolder[folders.length];

    for (int i = 0; i < folders.length; i++) {
      final VirtualFile folder = folders[i];
      final String folderPath = FileUtil.toSystemDependentName(folder.getPath());
      result[i] = new BufferingFolderWrapper(new File(folderPath));
    }
    return result;
  }

  public static void loadResources(@NotNull ResourceRepository repository,
                                   @Nullable final String layoutXmlFileText,
                                   @Nullable VirtualFile layoutXmlFile,
                                   @NotNull IAbstractFolder... rootFolders) throws IOException, RenderingException {
    final ScanningContext scanningContext = new ScanningContext(repository);

    for (IAbstractFolder rootFolder : rootFolders) {
      for (IAbstractResource file : rootFolder.listMembers()) {
        if (!(file instanceof IAbstractFolder)) {
          continue;
        }

        final IAbstractFolder folder = (IAbstractFolder)file;
        final ResourceFolder resFolder = repository.processFolder(folder);

        if (resFolder != null) {
          for (final IAbstractResource childRes : folder.listMembers()) {

            if (childRes instanceof IAbstractFile) {
              final VirtualFile vFile;

              if (childRes instanceof BufferingFileWrapper) {
                final BufferingFileWrapper fileWrapper = (BufferingFileWrapper)childRes;
                final String filePath = FileUtil.toSystemIndependentName(fileWrapper.getOsLocation());
                vFile = LocalFileSystem.getInstance().findFileByPath(filePath);

                if (vFile != null && vFile == layoutXmlFile && layoutXmlFileText != null) {
                  resFolder.processFile(new MyFileWrapper(layoutXmlFileText, childRes), ResourceDeltaKind.ADDED, scanningContext);
                }
                else {
                  resFolder.processFile((IAbstractFile)childRes, ResourceDeltaKind.ADDED, scanningContext);
                }
              }
              else {
                LOG.error("childRes must be instance of " + BufferingFileWrapper.class.getName());
              }
            }
          }
        }
      }
    }

    final List<String> errors = scanningContext.getErrors();
    if (errors != null && errors.size() > 0) {
      LOG.debug(new RenderingException(merge(errors)));
    }
  }

  private static String merge(@NotNull Collection<String> strs) {
    final StringBuilder result = new StringBuilder();
    for (Iterator<String> it = strs.iterator(); it.hasNext(); ) {
      String str = it.next();
      result.append(str);
      if (it.hasNext()) {
        result.append('\n');
      }
    }
    return result.toString();
  }

  @Nullable
  public static String getRClassName(@NotNull final Module module) {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Nullable
      @Override
      public String compute() {
        final AndroidFacet facet = AndroidFacet.getInstance(module);
        if (facet == null) {
          return null;
        }

        final Manifest manifest = facet.getManifest();
        if (manifest == null) {
          return null;
        }

        final String aPackage = manifest.getPackage().getValue();
        return aPackage == null ? null : aPackage + ".R";
      }
    });
  }

  @NotNull
  private static ProjectResources loadProjectResources(@NotNull AndroidFacet facet,
                                                       @Nullable String layoutXmlText,
                                                       @Nullable VirtualFile layoutXmlFile,
                                                       @NotNull List<ProjectResources> libResources)
    throws IOException, RenderingException {

    final VirtualFile resourceDir = facet.getLocalResourceManager().getResourceDir();

    if (resourceDir != null) {
      final IAbstractFolder resFolder = new BufferingFolderWrapper(new File(
        FileUtil.toSystemDependentName(resourceDir.getPath())));
      final ProjectResources projectResources = new ProjectResources(resFolder, libResources);
      loadResources(projectResources, layoutXmlText, layoutXmlFile, resFolder);
      return projectResources;
    }
    return new ProjectResources(new NullFolderWrapper(), libResources);
  }

  private static class MyFileWrapper implements IAbstractFile {
    private final String myLayoutXmlFileText;
    private final IAbstractResource myChildRes;

    public MyFileWrapper(String layoutXmlFileText, IAbstractResource childRes) {
      myLayoutXmlFileText = layoutXmlFileText;
      myChildRes = childRes;
    }

    @Override
    public InputStream getContents() throws StreamException {
      return new ByteArrayInputStream(myLayoutXmlFileText.getBytes());
    }

    @Override
    public void setContents(InputStream source) throws StreamException {
      throw new UnsupportedOperationException();
    }

    @Override
    public OutputStream getOutputStream() throws StreamException {
      throw new UnsupportedOperationException();
    }

    @Override
    public PreferredWriteMode getPreferredWriteMode() {
      throw new UnsupportedOperationException();
    }

    @Override
    public long getModificationStamp() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
      return myChildRes.getName();
    }

    @Override
    public String getOsLocation() {
      return myChildRes.getOsLocation();
    }

    @Override
    public boolean exists() {
      return true;
    }

    @Override
    public IAbstractFolder getParentFolder() {
      return myChildRes.getParentFolder();
    }

    @Override
    public boolean delete() {
      throw new UnsupportedOperationException();
    }
  }
}
