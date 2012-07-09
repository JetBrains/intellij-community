/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.vcsUtil;

import com.intellij.openapi.actionSystem.AnActionEvent;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 7/6/12
 * Time: 7:43 PM
 */
public class ActionUpdateHelper extends AbstractActionStateConsumer {
  public void apply(final AnActionEvent e) {
    e.getPresentation().setVisible(myVisible);
    e.getPresentation().setEnabled(myEnabled);
  }
}
