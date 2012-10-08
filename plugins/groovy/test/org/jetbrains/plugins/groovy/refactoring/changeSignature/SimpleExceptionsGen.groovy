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
package org.jetbrains.plugins.groovy.refactoring.changeSignature
import com.intellij.psi.PsiMethod
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo
/**
* @author Max Medvedev
*/
class SimpleExceptionsGen implements ChangeSignatureTestCase.GenExceptions {
  private final List<ThrownExceptionInfo> myInfos;

  public SimpleExceptionsGen(List<ThrownExceptionInfo> infos) {
    myInfos = infos;
  }

  @Override
  public ThrownExceptionInfo[] genExceptions(PsiMethod method) {
    for (ThrownExceptionInfo info : myInfos) {
      info.updateFromMethod(method);
    }
    return myInfos;
  }
}
