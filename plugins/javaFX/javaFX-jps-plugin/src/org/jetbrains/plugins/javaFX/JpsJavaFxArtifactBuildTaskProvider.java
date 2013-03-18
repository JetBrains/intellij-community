package org.jetbrains.plugins.javaFX;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.artifacts.ArtifactBuildTaskProvider;
import org.jetbrains.jps.incremental.BuildTask;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ExternalProcessUtil;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.elements.JpsArchivePackagingElement;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;
import org.jetbrains.plugins.javaFX.packaging.AbstractJavaFxPackager;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * User: anna
 * Date: 3/13/13
 */
public class JpsJavaFxArtifactBuildTaskProvider extends ArtifactBuildTaskProvider {

  public static final String COMPILER_NAME = "Java FX Packager";

  @NotNull
  @Override
  public List<? extends BuildTask> createArtifactBuildTasks(@NotNull JpsArtifact artifact,
                                                            @NotNull ArtifactBuildPhase buildPhase) {
    if (buildPhase != ArtifactBuildPhase.POST_PROCESSING) {
      return Collections.emptyList();
    }

    if (!(artifact.getArtifactType() instanceof JpsJavaFxApplicationArtifactType)) {
      return Collections.emptyList();
    }
    final JpsElement props = artifact.getProperties();

    if (!(props instanceof JpsJavaFxArtifactProperties)) {
      return Collections.emptyList();
    }

    return Collections.singletonList(new JavaFxJarDeployTask((JpsJavaFxArtifactProperties)props, artifact));
  }

  private static class JavaFxJarDeployTask extends BuildTask {

    private final JpsJavaFxArtifactProperties myProps;
    private final JpsArtifact myArtifact;

    public JavaFxJarDeployTask(JpsJavaFxArtifactProperties props, JpsArtifact artifact) {
      myProps = props;
      myArtifact = artifact;
    }

    @Override
    public void build(CompileContext context) throws ProjectBuildException {
      final Set<JpsSdk<?>> sdks = context.getProjectDescriptor().getProjectJavaSdks();
      JpsSdk javaSdk = null;
      for (JpsSdk<?> sdk : sdks) {
        final JpsSdkType<? extends JpsElement> sdkType = sdk.getSdkType();
        if (sdkType instanceof JpsJavaSdkType) {
          javaSdk = sdk;
          break;
        }
      }
      if (javaSdk == null) {
        context.processMessage(new CompilerMessage(COMPILER_NAME, BuildMessage.Kind.ERROR, "Java version 7 or higher is required to build JavaFX package"));
        return;
      }
      new JpsJavaFxPackager(myProps, context, myArtifact).createJarAndDeploy(javaSdk.getHomePath() + File.separator + "bin");
    }
  }

  private static class JpsJavaFxPackager extends AbstractJavaFxPackager {
    private final JpsJavaFxArtifactProperties myProperties;
    private final CompileContext myCompileContext;
    private final JpsArtifact myArtifact;

    public JpsJavaFxPackager(JpsJavaFxArtifactProperties properties, CompileContext compileContext, JpsArtifact artifact) {
      myArtifact = artifact;
      myProperties = properties;
      myCompileContext = compileContext;
    }

    @Override
    protected String getArtifactRootName() {
      return ((JpsArchivePackagingElement)myArtifact.getRootElement()).getArchiveName();
    }

    @Override
    protected String getArtifactOutputPath() {
      return myArtifact.getOutputPath();
    }

    @Override
    protected String getArtifactOutputFilePath() {
      return myArtifact.getOutputFilePath();
    }

    @Override
    protected String getAppClass() {
      return myProperties.myState.getAppClass();
    }

    @Override
    protected String getTitle() {
      return myProperties.myState.getTitle();
    }

    @Override
    protected String getVendor() {
      return myProperties.myState.getVendor();
    }

    @Override
    protected String getDescription() {
      return myProperties.myState.getDescription();
    }

    @Override
    protected String getWidth() {
      return myProperties.myState.getWidth();
    }

    @Override
    protected String getHeight() {
      return myProperties.myState.getHeight();
    }

    @Override
    protected void registerJavaFxPackagerError(String message) {
      myCompileContext.processMessage(new CompilerMessage(COMPILER_NAME, BuildMessage.Kind.ERROR, message));
    }

    @Override
    protected String prepareParam(String param) {
      return ExternalProcessUtil.prepareCommand(param);
    }

    @Override
    protected String getHtmlParamFile() {
      return myProperties.myState.getHtmlParamFile();
    }

    @Override
    protected String getParamFile() {
      return myProperties.myState.getParamFile();
    }

    @Override
    protected String getUpdateMode() {
      return myProperties.myState.getUpdateMode();
    }

    @Override
    public String getKeypass() {
      return myProperties.myState.getKeypass();
    }

    @Override
    public String getStorepass() {
      return myProperties.myState.getStorepass();
    }

    @Override
    public String getKeystore() {
      return myProperties.myState.getKeystore();
    }

    @Override
    public String getAlias() {
      return myProperties.myState.getAlias();
    }

    @Override
    public boolean isSelfSigning() {
      return myProperties.myState.isSelfSigning();
    }

    @Override
    public boolean isEnabledSigning() {
      return myProperties.myState.isEnabledSigning();
    }
  }
}


