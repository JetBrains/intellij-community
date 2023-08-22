// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.imports;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GrImportAlias;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrStubElementBase;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrImportStatementStub;
import org.jetbrains.plugins.groovy.lang.resolve.imports.GroovyImport;

import java.util.Objects;

import static org.jetbrains.plugins.groovy.lang.psi.impl.utils.PsiImportUtil.createImportFromStatement;

public class GrImportStatementImpl extends GrStubElementBase<GrImportStatementStub> implements GrImportStatement, StubBasedPsiElement<GrImportStatementStub> {

  public GrImportStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrImportStatementImpl(@NotNull GrImportStatementStub stub, @NotNull IStubElementType nodeType) {
    super(stub, nodeType);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitImportStatement(this);
  }

  @Override
  public String toString() {
    return "Import statement";
  }

  @Nullable
  private PsiClass resolveQualifier() {
    return CachedValuesManager.getCachedValue(this, () -> {
      GrCodeReferenceElement reference = getImportReference();
      GrCodeReferenceElement qualifier = reference == null ? null : reference.getQualifier();
      PsiElement target = qualifier == null ? null : qualifier.resolve();
      PsiClass clazz = target instanceof PsiClass ? (PsiClass)target : null;
      return CachedValueProvider.Result.create(clazz, PsiModificationTracker.MODIFICATION_COUNT, this);
    });
  }

  @Override
  public GrCodeReferenceElement getImportReference() {
    return findChildByType(GroovyElementTypes.REFERENCE_ELEMENT);
  }

  @Nullable
  @Override
  public String getImportFqn() {
    GrImportStatementStub stub = getGreenStub();
    if (stub != null) {
      return stub.getFqn();
    }
    GrCodeReferenceElement reference = getImportReference();
    return reference == null ? null : reference.getQualifiedReferenceName();
  }

  @Override
  @Nullable
  public String getImportedName() {
    if (isOnDemand()) return null;

    GrImportStatementStub stub = getStub();
    if (stub != null) {
      String name = stub.getAliasName();
      if (name != null) {
        return name;
      }

      String referenceText = stub.getFqn();
      return referenceText == null ? null : StringUtil.getShortName(referenceText);
    }

    GrImportAlias alias = getAlias();
    if (alias != null) {
      String aliasName = alias.getName();
      if (aliasName != null) {
        return aliasName;
      }
    }

    GrCodeReferenceElement ref = getImportReference();
    return ref == null ? null : ref.getReferenceName();
  }

  @Override
  public boolean isStatic() {
    GrImportStatementStub stub = getStub();
    if (stub != null) {
      return stub.isStatic();
    }

    return findChildByType(GroovyTokenTypes.kSTATIC) != null;
  }

  @Override
  public boolean isAliasedImport() {
    GrImportStatementStub stub = getStub();
    if (stub != null) {
      return stub.getAliasName() != null;
    }
    GrImportAlias alias = getAlias();
    return alias != null && alias.getName() != null;
  }

  @Override
  public boolean isOnDemand() {
    GrImportStatementStub stub = getStub();
    if (stub != null) {
      return stub.isOnDemand();
    }
    return findChildByType(GroovyTokenTypes.mSTAR) != null;
  }

  @Override
  @NotNull
  public GrModifierList getAnnotationList() {
    GrImportStatementStub stub = getStub();
    if (stub != null) {
      return Objects.requireNonNull(getStubOrPsiChild(GroovyStubElementTypes.MODIFIER_LIST));
    }
    return findNotNullChildByClass(GrModifierList.class);
  }

  @Nullable
  @Override
  public PsiClass resolveTargetClass() {
    final GrCodeReferenceElement ref = getImportReference();
    if (ref == null) return null;

    final PsiElement resolved;
    if (!isStatic() || isOnDemand()) {
      resolved = ref.resolve();
    }
    else {
      resolved = resolveQualifier();
    }

    return resolved instanceof PsiClass ? (PsiClass)resolved : null;
  }

  @Nullable
  @Override
  public GrImportAlias getAlias() {
    return findChildByClass(GrImportAlias.class);
  }

  @Nullable
  @Override
  public GroovyImport getImport() {
    return createImportFromStatement(this);
  }
}
