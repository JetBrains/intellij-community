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
package org.jetbrains.plugins.javaFX.packaging;

import com.intellij.compiler.ant.BuildProperties;
import com.intellij.compiler.ant.Generator;
import com.intellij.compiler.ant.Tag;
import com.intellij.compiler.ant.artifacts.DirectoryAntCopyInstructionCreator;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.*;
import com.intellij.packaging.impl.elements.CompositeElementWithManifest;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
* User: anna
* Date: 3/13/13
*/
public class JavaFxRootPackagingElement extends CompositeElementWithManifest<JavaFxRootPackagingElement> {

  private String myArtifactFile;

  public JavaFxRootPackagingElement(String artifactName) {
    super(JavaFxRootPackagingElementType.JAVAFX_ROOT_ELEMENT_TYPE);
    myArtifactFile = artifactName + ".jar";
  }

  public JavaFxRootPackagingElement() {
    super(JavaFxRootPackagingElementType.JAVAFX_ROOT_ELEMENT_TYPE);
  }

  @Override
  public PackagingElementPresentation createPresentation(@NotNull ArtifactEditorContext context) {
    return new JavaFxPackagingElementPresentation(myArtifactFile);
  }

  @Override
  public List<? extends Generator> computeAntInstructions(@NotNull PackagingElementResolvingContext resolvingContext,
                                                          @NotNull AntCopyInstructionCreator creator,
                                                          @NotNull ArtifactAntGenerationContext generationContext,
                                                          @NotNull ArtifactType artifactType) {
    final String artifactName = FileUtil.getNameWithoutExtension(myArtifactFile);
    String tempPathToFileSet = BuildProperties.propertyRef("artifact.temp.output." + artifactName);
    for (Generator generator : computeChildrenGenerators(resolvingContext, new DirectoryAntCopyInstructionCreator(tempPathToFileSet),
                                                         generationContext, artifactType)) {
      generationContext.runBeforeCurrentArtifact(generator);
    }
    final Sdk[] jdks = BuildProperties.getUsedJdks(resolvingContext.getProject());
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
                                  new Pair<String, String>("classpath", BuildProperties.propertyRef(
                                    BuildProperties.getJdkHomeProperty(javaSdk.getName())) + "/lib/ant-javafx.jar"));
      generationContext.runBeforeCurrentArtifact(taskdef);

      final Artifact artifact = ArtifactManager.getInstance(resolvingContext.getProject()).findArtifact(artifactName);
      if (artifact != null) {
        final JavaFxArtifactProperties properties =
          (JavaFxArtifactProperties)artifact.getProperties(JavaFxArtifactPropertiesProvider.getInstance());

        final Pair<String, String> namespacePair = new Pair<String, String>("xmlns:fx", "javafx:com.sun.javafx.tools.ant");

        //register application
        final String appId = artifactName + "_id";
        final Tag applicationTag = new Tag("fx:application",
                                           new Pair<String, String>("id", appId),
                                           new Pair<String, String>("name", artifactName),
                                           new Pair<String, String>("mainClass", properties.getAppClass()),
                                           namespacePair);
        generationContext.runBeforeCurrentArtifact(applicationTag);

        //create jar task
        final Tag createJarTag = new Tag("fx:jar", 
                                         new Pair<String, String>("destfile", tempPathToFileSet + "/" + myArtifactFile),
                                         namespacePair);
        createJarTag.add(new Tag("fx:application", new Pair<String, String>("refid", appId)));
        createJarTag.add(new Tag("fileset", new Pair<String, String>("dir", tempPathToFileSet)));
        generationContext.runBeforeCurrentArtifact(createJarTag);

        //deploy task
        final Tag deployTag = new Tag("fx:deploy", 
                                      new Pair<String, String>("width", properties.getWidth()),
                                      new Pair<String, String>("height", properties.getHeight()),
                                      new Pair<String, String>("updatemode", properties.getUpdateMode()),
                                      new Pair<String, String>("outdir", tempPathToFileSet + "/deploy"),
                                      new Pair<String, String>("outfile", artifactName),
                                      namespacePair);
        deployTag.add(new Tag("fx:application", new Pair<String, String>("refid", appId)));
        
        deployTag.add(new Tag("fx:info", 
                              new Pair<String, String>("title", properties.getTitle()),
                              new Pair<String, String>("vendor", properties.getVendor()),
                              new Pair<String, String>("description", properties.getDescription())));
        final Tag deployResourcesTag = new Tag("fx:resources");
        deployResourcesTag.add(new Tag("fx:fileset", new Pair<String, String>("dir", tempPathToFileSet), new Pair<String, String>("includes", myArtifactFile)));
        deployTag.add(deployResourcesTag);
        
        generationContext.runBeforeCurrentArtifact(deployTag);
      }
    }
    final Tag deleteTag = new Tag("delete", new Pair<String, String>("includeemptydirs", "true"));
    final Tag deleteFileSetTag = new Tag("fileset", new Pair<String, String>("dir", "${artifact.temp.output.unnamed}"));
    deleteFileSetTag.add(new Tag("exclude", new Pair<String, String>("name", artifactName + ".jar")));
    deleteFileSetTag.add(new Tag("exclude", new Pair<String, String>("name", artifactName + ".jnlp")));
    deleteFileSetTag.add(new Tag("exclude", new Pair<String, String>("name", artifactName + ".html")));
    deleteTag.add(deleteFileSetTag);
    return Arrays.asList(creator.createDirectoryContentCopyInstruction(tempPathToFileSet + "/deploy"), deleteTag);
  }

  @Override
  public void computeIncrementalCompilerInstructions(@NotNull IncrementalCompilerInstructionCreator creator,
                                                     @NotNull PackagingElementResolvingContext resolvingContext,
                                                     @NotNull ArtifactIncrementalCompilerContext compilerContext,
                                                     @NotNull ArtifactType artifactType) {
    computeChildrenInstructions(creator.archive(myArtifactFile), resolvingContext, compilerContext, artifactType);
  }

  @Override
  public boolean isEqualTo(@NotNull PackagingElement<?> element) {
    return element instanceof JavaFxRootPackagingElement && Comparing.strEqual(myArtifactFile, ((JavaFxRootPackagingElement)element).getName());
  }

  public void loadState(JavaFxRootPackagingElement state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @Attribute("name")
  public String getArtifactFile() {
    return myArtifactFile;
  }

  public void setArtifactFile(String artifactFile) {
    myArtifactFile = artifactFile;
  }

  @Override
  public JavaFxRootPackagingElement getState() {
    return this;
  }

  @Override
  public String getName() {
    return myArtifactFile;
  }

  @Override
  public void rename(@NotNull String newName) {
    myArtifactFile = newName;
  }
}
