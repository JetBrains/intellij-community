// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrReflectedMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrMethodStub;

/**
 * @author Dmitry.Krasilschikov
 */
public class GrConstructorImpl extends GrMethodBaseImpl implements GrMethod {
  public GrConstructorImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrConstructorImpl(GrMethodStub stub) {
    super(stub, GroovyStubElementTypes.CONSTRUCTOR);
  }

  @Override
  public String toString() {
    return "Constructor";
  }

  @Override
  public boolean isConstructor() {
    return true;
  }

  @Override
  public GrReflectedMethod @NotNull [] getReflectedMethods() {
    return CachedValuesManager.getCachedValue(this,
                                              () -> CachedValueProvider.Result
                                                .create(GrReflectedMethodImpl.createReflectedConstructors(this),
                                                        PsiModificationTracker.MODIFICATION_COUNT));
  }
}
