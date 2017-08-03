/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.grape;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.runner.DefaultGroovyScriptRunner;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunConfiguration;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

/**
 * @author peter
 */
public class GrabDependencies implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.grape.GrabDependencies");

  private static final NotificationGroup NOTIFICATION_GROUP = new NotificationGroup("Grape", NotificationDisplayType.BALLOON, true);
  public static final String GRAPE_RUNNER = "org.jetbrains.plugins.groovy.grape.GrapeRunner";

  @Override
  @NotNull
  public String getText() {
    return "Grab the artifacts";
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return "Grab";
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!isCorrectModule(file)) return false;

    int offset = editor.getCaretModel().getOffset();
    final GrAnnotation anno = PsiTreeUtil.findElementOfClassAtOffset(file, offset, GrAnnotation.class, false);
    if (anno != null && isGrabAnnotation(anno)) {
      return true;
    }

    PsiElement at = file.findElementAt(offset);
    if (at != null && isUnresolvedRefName(at) && findGrab(file) != null) {
      return true;
    }

    return false;
  }

  private static PsiAnnotation findGrab(final PsiFile file) {
    if (!(file instanceof GroovyFile)) return null;

    return CachedValuesManager.getCachedValue(file, () -> {
      PsiClass grab = JavaPsiFacade.getInstance(file.getProject()).findClass(GrabAnnos.GRAB_ANNO, file.getResolveScope());
      final Ref<PsiAnnotation> result = Ref.create();
      if (grab != null) {
        ReferencesSearch.search(grab, new LocalSearchScope(file)).forEach(reference -> {
          if (reference instanceof GrCodeReferenceElement) {
            PsiElement parent = ((GrCodeReferenceElement)reference).getParent();
            if (parent instanceof PsiAnnotation) {
              result.set((PsiAnnotation)parent);
              return false;
            }
          }
          return true;
        });
      }
      return CachedValueProvider.Result.create(result.get(), file);
    });
  }

  private static boolean isUnresolvedRefName(@NotNull PsiElement at) {
    PsiElement parent = at.getParent();
    return parent instanceof GrReferenceElement && ((GrReferenceElement)parent).getReferenceNameElement() == at && ((GrReferenceElement)parent).resolve() == null;
  }

  private static boolean isGrabAnnotation(@NotNull GrAnnotation anno) {
    final String qname = anno.getQualifiedName();
    return qname != null && (qname.startsWith(GrabAnnos.GRAB_ANNO) || GrabAnnos.GRAPES_ANNO.equals(qname));
  }

  private static boolean isCorrectModule(PsiFile file) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(file);
    if (module == null) {
      return false;
    }

    final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    if (sdk == null) {
      return false;
    }

    return file.getOriginalFile().getVirtualFile() != null && sdk.getSdkType() instanceof JavaSdkType;
  }

  @Override
  public void invoke(@NotNull final Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final Module module = ModuleUtilCore.findModuleForPsiElement(file);
    assert module != null;

    final VirtualFile vfile = file.getOriginalFile().getVirtualFile();
    assert vfile != null;

    if (JavaPsiFacade.getInstance(project).findClass("org.apache.ivy.core.report.ResolveReport", file.getResolveScope()) == null) {
      Messages.showErrorDialog("Sorry, but IDEA cannot @Grab the dependencies without Ivy. Please add Ivy to your module dependencies and re-run the action.",
                               "Ivy Missing");
      return;
    }

    Map<String, String> queries = prepareQueries(file);

    final Map<String, GeneralCommandLine> lines = new HashMap<>();
    for (String grabText : queries.keySet()) {
      final JavaParameters javaParameters = GroovyScriptRunConfiguration.createJavaParametersWithSdk(module);
      //debug
      //javaParameters.getVMParametersList().add("-Xdebug");
      //javaParameters.getVMParametersList().add("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5239");

      try {
        DefaultGroovyScriptRunner.configureGenericGroovyRunner(javaParameters, module, GRAPE_RUNNER, false, true, true, false);
        javaParameters.getClassPath().add(PathUtil.getJarPathForClass(GrapeRunner.class));
        javaParameters.getProgramParametersList().add(queries.get(grabText));
        javaParameters.setUseDynamicClasspath(true);
        lines.put(grabText, javaParameters.toCommandLine());
      }
      catch (CantRunException e) {
        String title = "Can't run @Grab: " + ExceptionUtil.getMessage(e);
        NOTIFICATION_GROUP.createNotification(title, ExceptionUtil.getThrowableText(e), NotificationType.ERROR, null).notify(project);
        return;
      }
    }

    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Processing @Grab Annotations") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        int jarCount = 0;
        String messages = "";

        for (Map.Entry<String, GeneralCommandLine> entry : lines.entrySet()) {
          String grabText = entry.getKey();
          indicator.setText2(grabText);
          try {
            final GrapeProcessHandler handler = new GrapeProcessHandler(entry.getValue(), module);
            handler.startNotify();
            handler.waitFor();
            jarCount += handler.jarCount;
            messages += "<b>" + grabText + "</b>: " + handler.messages + "<p>";
          }
          catch (ExecutionException e) {
            LOG.error(e);
          }
        }

        final String finalMessages = messages;
        final String title = jarCount + " Grape dependency jar" + (jarCount == 1 ? "" : "s") + " added";
        NOTIFICATION_GROUP.createNotification(title, finalMessages, NotificationType.INFORMATION, null).notify(project);
      }
    });
  }

  static Map<String, String> prepareQueries(PsiFile file) {
    final Set<GrAnnotation> grabs = new LinkedHashSet<>();
    final Set<GrAnnotation> excludes = new THashSet<>();
    final Set<GrAnnotation> resolvers = new THashSet<>();
    file.acceptChildren(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (element instanceof GrAnnotation) {
          GrAnnotation anno = (GrAnnotation)element;
          String qname = anno.getQualifiedName();
          if (GrabAnnos.GRAB_ANNO.equals(qname)) grabs.add(anno);
          else if (GrabAnnos.GRAB_EXCLUDE_ANNO.equals(qname)) excludes.add(anno);
          else if (GrabAnnos.GRAB_RESOLVER_ANNO.equals(qname)) resolvers.add(anno);
        }
        super.visitElement(element);
      }
    });

    Function<GrAnnotation, String> mapper = grAnnotation -> grAnnotation.getText();
    String common = StringUtil.join(excludes, mapper, " ") + " " + StringUtil.join(resolvers, mapper, " ");
    LinkedHashMap<String, String> result = new LinkedHashMap<>();
    for (GrAnnotation grab : grabs) {
      String grabText = grab.getText();
      result.put(grabText, (grabText + " " + common).trim());
    }
    return result;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  private static class GrapeProcessHandler extends OSProcessHandler {
    private final StringBuilder myStdOut = new StringBuilder();
    private final StringBuilder myStdErr = new StringBuilder();
    private final Module myModule;

    public GrapeProcessHandler(GeneralCommandLine commandLine, Module module) throws ExecutionException {
      super(commandLine);
      myModule = module;
    }

    @Override
    public void notifyTextAvailable(String text, Key outputType) {
      text = StringUtil.convertLineSeparators(text);
      if (LOG.isDebugEnabled()) {
        LOG.debug(outputType + text);
      }
      if (outputType == ProcessOutputTypes.STDOUT) {
        myStdOut.append(text);
      }
      else if (outputType == ProcessOutputTypes.STDERR) {
        myStdErr.append(text);
      }
    }

    private void addGrapeDependencies(List<VirtualFile> jars) {
      final ModifiableRootModel model = ModuleRootManager.getInstance(myModule).getModifiableModel();
      final LibraryTable.ModifiableModel tableModel = model.getModuleLibraryTable().getModifiableModel();
      for (VirtualFile jar : jars) {
        final VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(jar);
        if (jarRoot != null) {
          OrderRootType rootType = OrderRootType.CLASSES;
          String libName = "Grab:" + jar.getName();
          for (String classifier : ContainerUtil.ar("sources", "source", "src")) {
            if (libName.endsWith("-" + classifier + ".jar")) {
              rootType = OrderRootType.SOURCES;
              libName = StringUtil.trimEnd(libName, "-" + classifier + ".jar") + ".jar";
            }
          }

          Library library = tableModel.getLibraryByName(libName);
          if (library == null) {
            library = tableModel.createLibrary(libName);
          }

          final Library.ModifiableModel libModel = library.getModifiableModel();
          for (String url : libModel.getUrls(rootType)) {
            libModel.removeRoot(url, rootType);
          }
          libModel.addRoot(jarRoot, rootType);
          libModel.commit();
        }
      }
      tableModel.commit();
      model.commit();
    }

    int jarCount;
    String messages = "";

    @Override
    protected void notifyProcessTerminated(int exitCode) {
      try {
        final List<VirtualFile> jars = new ArrayList<>();
        for (String line : myStdOut.toString().split("\n")) {
          if (line.startsWith(GrapeRunner.URL_PREFIX)) {
            try {
              final URL url = new URL(line.substring(GrapeRunner.URL_PREFIX.length()));
              final File libFile = new File(url.toURI());
              if (libFile.exists() && libFile.getName().endsWith(".jar")) {
                ContainerUtil.addIfNotNull(jars, LocalFileSystem.getInstance().refreshAndFindFileByIoFile(libFile));
              }
            }
            catch (MalformedURLException | URISyntaxException e) {
              LOG.error(e);
            }
          }
        }
        new WriteAction() {
          @Override
          protected void run(@NotNull Result result) throws Throwable {
            jarCount = jars.size();
            messages = jarCount + " jar";
            if (jarCount != 1) {
              messages += "s";
            }
            if (jarCount == 0) {
              messages += "<br>" + myStdOut.toString().replaceAll("\n", "<br>") + "<p>" + myStdErr.toString().replaceAll("\n", "<br>");
            }
            if (!jars.isEmpty()) {
              addGrapeDependencies(jars);
            }
          }
        }.execute();
      }
      finally {
        super.notifyProcessTerminated(exitCode);
      }
    }
  }
}
