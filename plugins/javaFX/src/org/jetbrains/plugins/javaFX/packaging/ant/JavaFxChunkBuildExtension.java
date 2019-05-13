/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.javaFX.packaging.ant;

import com.intellij.compiler.ant.*;
import com.intellij.compiler.ant.artifacts.DirectoryAntCopyInstructionCreator;
import com.intellij.compiler.ant.taskdefs.Property;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.*;
import com.intellij.packaging.impl.elements.ArchivePackagingElement;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.packaging.JavaFxAntGenerator;
import org.jetbrains.plugins.javaFX.packaging.JavaFxApplicationArtifactType;
import org.jetbrains.plugins.javaFX.packaging.JavaFxArtifactProperties;
import org.jetbrains.plugins.javaFX.packaging.JavaFxArtifactPropertiesProvider;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;

public class JavaFxChunkBuildExtension extends ChunkBuildExtension {

  @NonNls public static final String ARTIFACT_VENDOR_SIGN_PROPERTY = "artifact.sign.vendor";
  @NonNls public static final String ARTIFACT_ALIAS_SIGN_PROPERTY = "artifact.sign.alias";
  @NonNls public static final String ARTIFACT_KEYSTORE_SIGN_PROPERTY = "artifact.sign.keystore";
  @NonNls public static final String ARTIFACT_STOREPASS_SIGN_PROPERTY = "artifact.sign.storepass";
  @NonNls public static final String ARTIFACTKEYPASS_SIGN_PROPERTY = "artifact.sign.keypass";

  @NotNull
  @Override
  public String[] getTargets(ModuleChunk chunk) {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  public void process(Project project, ModuleChunk chunk, GenerationOptions genOptions, CompositeGenerator generator) {}

  @Override
  public void initArtifacts(Project project, GenerationOptions genOptions, CompositeGenerator generator) {
    final Collection<? extends Artifact> artifacts =
      ArtifactManager.getInstance(project).getArtifactsByType(JavaFxApplicationArtifactType.getInstance());
    if (artifacts.isEmpty()) return;
    final Sdk[] jdks = BuildProperties.getUsedJdks(project);
    Sdk javaSdk = null;
    for (Sdk jdk : jdks) {
      if (jdk.getSdkType() instanceof JavaSdkType) {
        javaSdk = jdk;
        break;
      }
    }
    if (javaSdk != null) {
      final Tag taskdef = new Tag("taskdef",
                                  Couple.of("resource", "com/sun/javafx/tools/ant/antlib.xml"),
                                  Couple.of("uri", "javafx:com.sun.javafx.tools.ant"),
                                  Couple.of("classpath",
                                            BuildProperties
                                              .propertyRef(BuildProperties.getJdkHomeProperty(javaSdk.getName())) +
                                            "/lib/ant-javafx.jar"));
      generator.add(taskdef);
    }
  }

  protected List<? extends Generator> computeChildrenGenerators(PackagingElementResolvingContext resolvingContext,
                                                                  final AntCopyInstructionCreator copyInstructionCreator,
                                                                  final ArtifactAntGenerationContext generationContext, 
                                                                  ArtifactType artifactType,
                                                                  List<PackagingElement<?>> children) {
      final List<Generator> generators = new ArrayList<>();
      for (PackagingElement<?> child : children) {
        generators.addAll(child.computeAntInstructions(resolvingContext, copyInstructionCreator, generationContext, artifactType));
      }
      return generators;
    }
  
  @Override
  public void generateTasksForArtifact(Artifact artifact,
                                       boolean preprocessing,
                                       ArtifactAntGenerationContext context,
                                       CompositeGenerator generator) {
    if (preprocessing) return;
    if (!(artifact.getArtifactType() instanceof JavaFxApplicationArtifactType)) return;

    final CompositePackagingElement<?> rootElement = artifact.getRootElement();

    final List<PackagingElement<?>> children = new ArrayList<>();
    String artifactFileName = rootElement.getName();
    for (PackagingElement<?> child : rootElement.getChildren()) {
      if (child instanceof ArchivePackagingElement) {
        artifactFileName = ((ArchivePackagingElement)child).getArchiveFileName();
        children.addAll(((ArchivePackagingElement)child).getChildren());
      } else {
        children.add(child);
      }
    }

    final String artifactName = FileUtil.getNameWithoutExtension(artifactFileName);

    final String tempDirPath = BuildProperties.propertyRef(
      context.createNewTempFileProperty("artifact.temp.output." + artifactName, artifactFileName));

    final PackagingElementResolvingContext resolvingContext = ArtifactManager.getInstance(context.getProject()).getResolvingContext();
    for (Generator childGenerator : computeChildrenGenerators(resolvingContext, 
                                                              new DirectoryAntCopyInstructionCreator(tempDirPath),
                                                         context, artifact.getArtifactType(), children)) {
      generator.add(childGenerator);
    }

    final JavaFxArtifactProperties properties =
      (JavaFxArtifactProperties)artifact.getProperties(JavaFxArtifactPropertiesProvider.getInstance());

    final JavaFxArtifactProperties.JavaFxPackager javaFxPackager =
      new JavaFxArtifactProperties.JavaFxPackager(artifact, properties, context.getProject()) {
        @Override
        protected void registerJavaFxPackagerError(String message) {}
        @Override
        protected void registerJavaFxPackagerInfo(String message) {}
      };
    final String tempDirDeployPath = tempDirPath + "/deploy";
    final List<JavaFxAntGenerator.SimpleTag> tags =
      JavaFxAntGenerator.createJarAndDeployTasks(javaFxPackager, artifactFileName, artifact.getName(), tempDirPath, tempDirDeployPath, context.getProject().getBasePath());
    for (JavaFxAntGenerator.SimpleTag tag : tags) {
      buildTags(generator, tag);
    }

    if (properties.isEnabledSigning()) {

      final boolean selfSigning = properties.isSelfSigning();
      String vendor = properties.getVendor();
      if (vendor != null) {
        vendor = vendor.replaceAll(",", "\\\\,")  ;
      }
      generator.add(new Property(artifactBasedProperty(ARTIFACT_VENDOR_SIGN_PROPERTY, artifactName), "CN=" + vendor));

      final String alias = selfSigning ? "jb" : properties.getAlias();
      generator.add(new Property(artifactBasedProperty(ARTIFACT_ALIAS_SIGN_PROPERTY, artifactName), alias));

      final String keystore = selfSigning ? tempDirPath + File.separator + "jb-key.jks" : properties.getKeystore();
      generator.add(new Property(artifactBasedProperty(ARTIFACT_KEYSTORE_SIGN_PROPERTY, artifactName), keystore));

      final String storepass = selfSigning ? "storepass" : new String(Base64.getDecoder().decode(properties.getStorepass()), StandardCharsets.UTF_8);
      generator.add(new Property(artifactBasedProperty(ARTIFACT_STOREPASS_SIGN_PROPERTY, artifactName), storepass));

      final String keypass = selfSigning ? "keypass" : new String(Base64.getDecoder().decode(properties.getKeypass()), StandardCharsets.UTF_8);
      generator.add(new Property(artifactBasedProperty(ARTIFACTKEYPASS_SIGN_PROPERTY, artifactName), keypass));
      
      final Pair[] keysDescriptions = createKeysDescriptions(artifactName);
      if (selfSigning) {
        generator.add(new Tag("genkey", 
                              ArrayUtil.prepend(Couple.of("dname", BuildProperties
                                                  .propertyRef(artifactBasedProperty(ARTIFACT_VENDOR_SIGN_PROPERTY, artifactName))),
                                                keysDescriptions)));
      }
      
      final Tag signjar = new Tag("signjar", keysDescriptions);
      final Tag fileset = new Tag("fileset", Couple.of("dir", tempDirDeployPath));
      fileset.add(new Tag("include", Couple.of("name", "*.jar")));
      signjar.add(fileset);
      generator.add(signjar);
    }

    final DirectoryAntCopyInstructionCreator creator = new DirectoryAntCopyInstructionCreator(BuildProperties.propertyRef(context.getConfiguredArtifactOutputProperty(artifact)));
    generator.add(creator.createDirectoryContentCopyInstruction(tempDirDeployPath));
    final Tag deleteTag = new Tag("delete", Couple.of("includeemptydirs", "true"));
    deleteTag.add(new Tag("fileset", Couple.of("dir", tempDirPath)));
    generator.add(deleteTag);
  }

  private static void buildTags(CompositeGenerator generator, final JavaFxAntGenerator.SimpleTag tag) {
    final Tag newTag = new Tag(tag.getName(), tag.getPairs()){
      @Override
      public void generate(PrintWriter out) throws IOException {
        final String value = tag.getValue();
        if (value == null) {
          super.generate(out);
        } else {
          out.print("<" + tag.getName() + ">" + value + "</" + tag.getName() + ">");
        }
      }
    };

    for (JavaFxAntGenerator.SimpleTag simpleTag : tag.getSubTags()) {
      buildTags(newTag, simpleTag);
    }
    generator.add(newTag);
  }

  private static String artifactBasedProperty(final String property, String artifactName) {
    return property + "." + artifactName;
  }

  private static Pair[] createKeysDescriptions(String artifactName) {
    return new Pair[]{
      Couple.of("alias", BuildProperties.propertyRef(artifactBasedProperty(ARTIFACT_ALIAS_SIGN_PROPERTY, artifactName))),
      Couple.of("keystore", BuildProperties.propertyRef(artifactBasedProperty(ARTIFACT_KEYSTORE_SIGN_PROPERTY, artifactName))),
      Couple.of("storepass", BuildProperties.propertyRef(artifactBasedProperty(ARTIFACT_STOREPASS_SIGN_PROPERTY, artifactName))),
      Couple.of("keypass", BuildProperties.propertyRef(artifactBasedProperty(ARTIFACTKEYPASS_SIGN_PROPERTY, artifactName)))};
  }

  @Nullable
  @Override
  public Couple<String> getArtifactXmlNs(ArtifactType artifactType) {
    if (artifactType instanceof JavaFxApplicationArtifactType) {
      return Couple.of("xmlns:fx", "javafx:com.sun.javafx.tools.ant");
    }
    return null;
  }

  @Override
  public boolean needAntArtifactInstructions(ArtifactType type) {
    if (type instanceof JavaFxApplicationArtifactType) {
      return false;
    }
    return true;
  }
}
