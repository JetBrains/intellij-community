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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Base64Converter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.packaging.JavaFxApplicationArtifactType;
import org.jetbrains.plugins.javaFX.packaging.JavaFxArtifactProperties;
import org.jetbrains.plugins.javaFX.packaging.JavaFxArtifactPropertiesProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * User: anna
 * Date: 3/14/13
 */
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
                                  new Pair<String, String>("resource", "com/sun/javafx/tools/ant/antlib.xml"),
                                  new Pair<String, String>("uri", "javafx:com.sun.javafx.tools.ant"),
                                  new Pair<String, String>("classpath",
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
      final List<Generator> generators = new ArrayList<Generator>();
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
    final CompositePackagingElement<?> rootElement = artifact.getRootElement();
    final String artifactName = FileUtil.getNameWithoutExtension(rootElement.getName());

    final String tempDirPath = BuildProperties.propertyRef(context.getArtifactOutputProperty(artifact));

    final List<PackagingElement<?>> children = rootElement.getChildren();
    final PackagingElementResolvingContext resolvingContext = ArtifactManager.getInstance(context.getProject()).getResolvingContext();
    for (Generator childGenerator : computeChildrenGenerators(resolvingContext, 
                                                              new DirectoryAntCopyInstructionCreator(tempDirPath),
                                                         context, artifact.getArtifactType(), children)) {
      generator.add(childGenerator);
    }
    
    final String artifactFileName = rootElement.getName();
    final JavaFxArtifactProperties properties =
      (JavaFxArtifactProperties)artifact.getProperties(JavaFxArtifactPropertiesProvider.getInstance());

    //register application
    final String appId = artifactName + "_id";
    final Tag applicationTag = new Tag("fx:application",
                                       new Pair<String, String>("id", appId),
                                       new Pair<String, String>("name", artifactName),
                                       new Pair<String, String>("mainClass", properties.getAppClass()));
    generator.add(applicationTag);

    //create jar task
    final Tag createJarTag = new Tag("fx:jar",
                                     new Pair<String, String>("destfile", tempDirPath + "/" + artifactFileName));
    createJarTag.add(new Tag("fx:application", new Pair<String, String>("refid", appId)));
    createJarTag.add(new Tag("fileset", new Pair<String, String>("dir", tempDirPath)));
    generator.add(createJarTag);

    //deploy task
    final Tag deployTag = new Tag("fx:deploy",
                                  new Pair<String, String>("width", properties.getWidth()),
                                  new Pair<String, String>("height", properties.getHeight()),
                                  new Pair<String, String>("updatemode", properties.getUpdateMode()),
                                  new Pair<String, String>("outdir", tempDirPath + "/deploy"),
                                  new Pair<String, String>("outfile", artifactName));
    deployTag.add(new Tag("fx:application", new Pair<String, String>("refid", appId)));

    deployTag.add(new Tag("fx:info",
                          new Pair<String, String>("title", properties.getTitle()),
                          new Pair<String, String>("vendor", properties.getVendor()),
                          new Pair<String, String>("description", properties.getDescription())));
    final Tag deployResourcesTag = new Tag("fx:resources");
    deployResourcesTag.add(new Tag("fx:fileset", new Pair<String, String>("dir", tempDirPath), 
                                                 new Pair<String, String>("includes", artifactFileName)));
    deployTag.add(deployResourcesTag);

    generator.add(deployTag);

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

      final String storepass = selfSigning ? "storepass" : Base64Converter.decode(properties.getStorepass());
      generator.add(new Property(artifactBasedProperty(ARTIFACT_STOREPASS_SIGN_PROPERTY, artifactName), storepass));

      final String keypass = selfSigning ? "keypass" : Base64Converter.decode(properties.getKeypass());
      generator.add(new Property(artifactBasedProperty(ARTIFACTKEYPASS_SIGN_PROPERTY, artifactName), keypass));
      
      final Pair[] keysDescriptions = createKeysDescriptions(artifactName);
      if (selfSigning) {
        generator.add(new Tag("genkey", 
                              ArrayUtil.prepend(new Pair<String, String>("dname", BuildProperties.propertyRef(artifactBasedProperty(ARTIFACT_VENDOR_SIGN_PROPERTY, artifactName))), 
                                                keysDescriptions)));
      }
      
      final Tag signjar = new Tag("signjar", keysDescriptions);
      final Tag fileset = new Tag("fileset", new Pair<String, String>("dir", tempDirPath + "/deploy"));
      fileset.add(new Tag("include", new Pair<String, String>("name", artifactFileName)));
      signjar.add(fileset);
      generator.add(signjar);
    }

    final DirectoryAntCopyInstructionCreator creator = new DirectoryAntCopyInstructionCreator(BuildProperties.propertyRef(context.getConfiguredArtifactOutputProperty(artifact)));
    generator.add(creator.createDirectoryContentCopyInstruction(tempDirPath + "/deploy"));
    final Tag deleteTag = new Tag("delete", new Pair<String, String>("includeemptydirs", "true"));
    deleteTag.add(new Tag("fileset", new Pair<String, String>("dir", tempDirPath)));
    generator.add(deleteTag);
  }

  private static String artifactBasedProperty(final String property, String artifactName) {
    return property + "." + artifactName;
  }

  private static Pair[] createKeysDescriptions(String artifactName) {
    return new Pair[]{
      new Pair<String, String>("alias", BuildProperties.propertyRef(artifactBasedProperty(ARTIFACT_ALIAS_SIGN_PROPERTY, artifactName))),
      new Pair<String, String>("keystore", BuildProperties.propertyRef(artifactBasedProperty(ARTIFACT_KEYSTORE_SIGN_PROPERTY, artifactName))),
      new Pair<String, String>("storepass", BuildProperties.propertyRef(artifactBasedProperty(ARTIFACT_STOREPASS_SIGN_PROPERTY, artifactName))),
      new Pair<String, String>("keypass", BuildProperties.propertyRef(artifactBasedProperty(ARTIFACTKEYPASS_SIGN_PROPERTY, artifactName)))};
  }

  @Nullable
  @Override
  public Pair<String, String> getArtifactXmlNs(ArtifactType artifactType) {
    if (artifactType instanceof JavaFxApplicationArtifactType) {
      return Pair.create("xmlns:fx", "javafx:com.sun.javafx.tools.ant");
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
