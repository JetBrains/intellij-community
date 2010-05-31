/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.execution.testframework.sm;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author Roman.Chernyatchik
 */
public abstract class SMLightFixtureTestCase extends UsefulTestCase {

  protected SMLightFixtureTestCase() {
    PlatformTestCase.initPlatformLangPrefix();
  }

  protected static final LightProjectDescriptor ourDescriptor = new LightProjectDescriptor() {
    public ModuleType getModuleType() {
      return ModuleType.EMPTY;
    }

    public Sdk getSdk() {
      return null;
    }

    public void configureModule(Module module, ModifiableRootModel model, ContentEntry contentEntry) {
      //Do nothing
    }
  };
  protected CodeInsightTestFixture myFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    final IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    final TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder =
      factory.createLightFixtureBuilder(getProjectDescriptor());

    final IdeaProjectTestFixture fixture = fixtureBuilder.getFixture();
    final LightTempDirTestFixtureImpl tempDirTestFixture = new LightTempDirTestFixtureImpl(true);
    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture, tempDirTestFixture);
    myFixture.setUp();

    setupFixtureWhenInitialized();
  }

  @Override
  protected void tearDown() throws Exception {
    myFixture.tearDown();
    myFixture = null;

    super.tearDown();
  }

  protected void setupFixtureWhenInitialized() throws IOException {
  }

  @Nullable
  protected LightProjectDescriptor getProjectDescriptor() {
    return null;
  }

  protected Project getProject() {
    return myFixture.getProject();
  }

  protected void createAndAddFile(final String relativePath, final String text) throws IOException {
    final PsiFile psiFile = myFixture.addFileToProject(relativePath, text);
    myFixture.configureFromExistingVirtualFile(psiFile.getVirtualFile());
  }
}
