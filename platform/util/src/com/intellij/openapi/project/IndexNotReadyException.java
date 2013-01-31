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
package com.intellij.openapi.project;

/**
 * Thrown on accessing indices in dumb mode. Possible fixes:
 * <li> if {@link com.intellij.openapi.actionSystem.AnAction#actionPerformed(com.intellij.openapi.actionSystem.AnActionEvent)} is in stack trace,
 * consider making the action not implement {@link com.intellij.openapi.project.DumbAware}.
 * <li> if this access is performed from some invokeLater activity, consider replacing it with
 * {@link com.intellij.openapi.project.DumbService#smartInvokeLater(Runnable)}
 * <li> otherwise, add {@link DumbService#isDumb()} checks where necessary
 *
 * @author peter
 * @see com.intellij.openapi.project.DumbService
 * @see com.intellij.openapi.project.DumbAware
 */
public class IndexNotReadyException extends RuntimeException {

  @Override
  public String getMessage() {
    return "Please change caller according to " + IndexNotReadyException.class.getName() + " documentation";
  }
}
