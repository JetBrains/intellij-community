/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.parser.files;


import static com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement.APPLY_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.model.BaseCompileOptionsModelImpl.SOURCE_COMPATIBILITY;
import static com.android.tools.idea.gradle.dsl.model.BaseCompileOptionsModelImpl.TARGET_COMPATIBILITY;
import static com.android.tools.idea.gradle.dsl.parser.java.JavaDslElement.JAVA;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;

import com.android.tools.idea.gradle.dsl.model.BuildModelContext;
import com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.java.JavaDslElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class GradleBuildFile extends GradleDslFile {
  public GradleBuildFile(@NotNull VirtualFile file,
                         @NotNull Project project,
                         @NotNull String moduleName,
                         @NotNull BuildModelContext context) {
    super(file, project, moduleName, context);
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement element) {
    if (APPLY_BLOCK_NAME.equals(element.getFullName())) {
      ApplyDslElement applyDslElement = getPropertyElement(APPLY_BLOCK_NAME, ApplyDslElement.class);
      if (applyDslElement == null) {
        applyDslElement = new ApplyDslElement(this);
        super.addParsedElement(applyDslElement);
      }
      applyDslElement.addParsedElement(element);
      return;
    }
    super.addParsedElement(element);
  }

  @Override
  public void setParsedElement(@NotNull GradleDslElement element) {
    // TODO(xof): doing explicit effect assignment here is ugly, but a GradleDslFile is not a GradleBlockDslElement, but a
    //  GradlePropertiesDslElement.  Maybe we should rethink where in the hierarchy the effect logic should go?
    ModelEffectDescription effect = null;

    if (element instanceof GradleDslLiteral) {
      // This is only in setParsedElement, not addParsedElement, because these are only supported as properties, not as setter methods.
      //
      // TODO(xof): these are only supported in Groovy.  Or maybe the criterion is something else?
      if (element.getName().equals("sourceCompatibility")) {
        effect = new ModelEffectDescription(new ModelPropertyDescription(SOURCE_COMPATIBILITY), SET);
      }
      else if (element.getName().equals("targetCompatibility")) {
        effect = new ModelEffectDescription(new ModelPropertyDescription(TARGET_COMPATIBILITY), SET);
      }
      else {
        super.setParsedElement(element);
        return;
      }
      JavaDslElement javaDslElement = getPropertyElement(JAVA);
      if (javaDslElement == null) {
        javaDslElement = new JavaDslElement(this, GradleNameElement.create(JAVA.name));
        setParsedElement(javaDslElement);
      }
      element.setModelEffect(effect);
      javaDslElement.setParsedElement(element);
      return;
    }

    super.setParsedElement(element);
  }
}
