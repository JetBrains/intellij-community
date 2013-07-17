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
package org.jetbrains.plugins.groovy.refactoring.introduce.field;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.refactoring.introduceField.IntroduceFieldHandler;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrInplaceIntroducer;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;

import javax.swing.*;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GrInplaceFieldIntroducer extends GrInplaceIntroducer {
  public GrInplaceFieldIntroducer(GrVariable var, GrIntroduceContext context, List<RangeMarker> occurrences) {
    super(var, context.getEditor(), context.getProject(), IntroduceFieldHandler.REFACTORING_NAME, occurrences, context.getElementToIntroduce());
  }


  @Nullable
  @Override
  protected JComponent getComponent() {
    return super.getComponent();
  }
}
