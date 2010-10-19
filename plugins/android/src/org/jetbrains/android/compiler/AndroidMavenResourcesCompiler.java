package org.jetbrains.android.compiler;

import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.compiler.tools.AndroidMavenExecutor;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.maven.AndroidMavenProvider;
import org.jetbrains.android.maven.AndroidMavenUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidMavenResourcesCompiler implements SourceGeneratingCompiler {
  private static final GenerationItem[] EMPTY_GENERATION_ITEM_ARRAY = {};

  public GenerationItem[] getGenerationItems(CompileContext context) {
    Module[] affectedModules = context.getCompileScope().getAffectedModules();
    if (affectedModules.length > 0) {
      Application application = ApplicationManager.getApplication();
      return application.runReadAction(new PrepareAction(context));
    }
    return EMPTY_GENERATION_ITEM_ARRAY;
  }

  public GenerationItem[] generate(final CompileContext context, final GenerationItem[] items, VirtualFile outputRootDirectory) {
    if (items != null && items.length > 0) {
      context.getProgressIndicator().setText("Copying resources from Maven artifacts...");
      Computable<GenerationItem[]> computation = new Computable<GenerationItem[]>() {
        public GenerationItem[] compute() {
          if (context.getProject().isDisposed()) {
            return EMPTY_GENERATION_ITEM_ARRAY;
          }
          return doGenerate(context, items);
        }
      };
      GenerationItem[] generationItems = computation.compute();
      for (GenerationItem item : generationItems) {
        File generatedFile = ((MyGenerationItem)item).myGeneratedFile;
        if (generatedFile != null) {
          CompilerUtil.refreshIOFile(generatedFile);
        }
      }
      return generationItems;
    }
    return EMPTY_GENERATION_ITEM_ARRAY;
  }

  private static GenerationItem[] doGenerate(final CompileContext context, GenerationItem[] items) {
    List<GenerationItem> results = new ArrayList<GenerationItem>(items.length);
    for (GenerationItem item : items) {
      if (item instanceof MyGenerationItem) {
        final MyGenerationItem genItem = (MyGenerationItem)item;
        Map<CompilerMessageCategory, List<String>> messages = AndroidMavenExecutor.generateResources(genItem.myModule);
        AndroidCompileUtil.addMessages(context, messages);
        if (messages.get(CompilerMessageCategory.ERROR).isEmpty()) {
          results.add(genItem);
        }
        if (genItem.myGeneratedFile.exists()) {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              String className = FileUtil.getNameWithoutExtension(genItem.myGeneratedFile);
              AndroidCompileUtil.removeDuplicatingClasses(genItem.myModule, genItem.myPackage, className, genItem.myGeneratedFile);
            }
          });
        }
      }
    }
    return results.toArray(new GenerationItem[results.size()]);
  }

  @NotNull
  public String getDescription() {
    return "Android Maven Resources Compiler";
  }

  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

  public ValidityState createValidityState(DataInput is) throws IOException {
    return new MyValidityState(is);
  }

  private final static class MyGenerationItem implements GenerationItem {
    final Module myModule;
    final String myPackage;
    final File myGeneratedFile;

    private MyGenerationItem(@NotNull Module module, @NotNull String aPackage, @NotNull String sourceRootPath) {
      myModule = module;
      myPackage = aPackage;
      myGeneratedFile =
        new File(sourceRootPath, aPackage.replace('.', File.separatorChar) + File.separator + AndroidUtils.R_JAVA_FILENAME);
    }

    @Nullable
    public String getPath() {
      return myPackage.replace('.', '/') + '/' + AndroidUtils.R_JAVA_FILENAME;
    }

    public ValidityState getValidityState() {
      return new MyValidityState(myModule);
    }

    public Module getModule() {
      return myModule;
    }

    public boolean isTestSource() {
      return false;
    }
  }

  private static class MyValidityState implements ValidityState {
    private final long[] myMavenArtifactsTimespamps;

    private MyValidityState(Module module) {
      AndroidMavenProvider mavenProvider = AndroidMavenUtil.getMavenProvider();
      assert mavenProvider != null;
      List<File> files = mavenProvider.getMavenDependencyArtifactFiles(module);
      myMavenArtifactsTimespamps = new long[files.size()];
      for (int i = 0, filesSize = files.size(); i < filesSize; i++) {
        myMavenArtifactsTimespamps[i] = files.get(i).lastModified();
      }
    }

    public MyValidityState(DataInput is) throws IOException {
      int c = is.readInt();
      myMavenArtifactsTimespamps = new long[c];
      for (int i = 0; i < c; i++) {
        myMavenArtifactsTimespamps[i] = is.readLong();
      }
    }

    @Override
    public boolean equalsTo(ValidityState otherState) {
      if (!(otherState instanceof MyValidityState)) {
        return false;
      }
      return Arrays.equals(myMavenArtifactsTimespamps, ((MyValidityState)otherState).myMavenArtifactsTimespamps);
    }

    @Override
    public void save(DataOutput out) throws IOException {
      out.writeInt(myMavenArtifactsTimespamps.length);
      for (long timespamp : myMavenArtifactsTimespamps) {
        out.writeLong(timespamp);
      }
    }
  }

  private static final class PrepareAction implements Computable<GenerationItem[]> {
    private final CompileContext myContext;

    public PrepareAction(CompileContext context) {
      myContext = context;
    }

    public GenerationItem[] compute() {
      if (myContext.getProject().isDisposed()) {
        return EMPTY_GENERATION_ITEM_ARRAY;
      }
      Module[] modules = myContext.getCompileScope().getAffectedModules();
      List<GenerationItem> items = new ArrayList<GenerationItem>();
      for (Module module : modules) {
        MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstance(myContext.getProject());
        if (mavenProjectsManager != null && mavenProjectsManager.isMavenizedModule(module)) {
          AndroidFacet facet = AndroidFacet.getInstance(module);
          if (facet != null && facet.getConfiguration().COPY_RESOURCES_FROM_ARTIFACTS) {
            Manifest manifest = facet.getManifest();
            String aPackage = manifest != null ? manifest.getPackage().getValue() : null;
            if (aPackage != null) {
              MavenProject mavenProject = mavenProjectsManager.findProject(module);
              if (mavenProject != null) {
                items.add(new MyGenerationItem(module, aPackage, mavenProject.getGeneratedSourcesDirectory(false) + "/r"));
              }
            }
          }
        }
      }
      return items.toArray(new GenerationItem[items.size()]);
    }
  }
}
