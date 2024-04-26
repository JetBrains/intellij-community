package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;

import java.util.List;

/**
 * @author Max Medvedev
 */
public class SimpleExceptionsGen implements ChangeSignatureTestCase.GenExceptions {
  public SimpleExceptionsGen(List<ThrownExceptionInfo> infos) {
    myInfos = infos.toArray(ThrownExceptionInfo[]::new);
  }

  @Override
  public ThrownExceptionInfo[] genExceptions(PsiMethod method) {
    for (ThrownExceptionInfo info : myInfos) {
      info.updateFromMethod(method);
    }

    return myInfos;
  }

  private final ThrownExceptionInfo[] myInfos;
}
