// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.plugins.groovy.lang.resolve.imports.ModuleImport;
import org.jetbrains.plugins.groovy.lang.resolve.imports.RegularImport;
import org.jetbrains.plugins.groovy.lang.resolve.imports.StarImport;
import org.jetbrains.plugins.groovy.lang.resolve.imports.StaticImport;
import org.jetbrains.plugins.groovy.lang.resolve.imports.StaticStarImport;

import java.util.Objects;

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

  private @Nullable PsiClass resolveQualifier() {
    return CachedValuesManager.getCachedValue(this, () -> {
      GrCodeReferenceElement reference = getImportReference();
      GrCodeReferenceElement qualifier = reference == null ? null : reference.getQualifier();
      PsiElement target = qualifier == null ? null : qualifier.resolve();
      PsiClass clazz = target instanceof PsiClass aClass ? aClass : null;
      return CachedValueProvider.Result.create(clazz, PsiModificationTracker.MODIFICATION_COUNT, this);
    });
  }

  @Override
  public GrCodeReferenceElement getImportReference() {
    return findChildByType(GroovyElementTypes.REFERENCE_ELEMENT);
  }

  @Override
  public @Nullable String getImportFqn() {
    GrImportStatementStub stub = getGreenStub();
    if (stub != null) {
      return stub.getFqn();
    }
    GrCodeReferenceElement reference = getImportReference();
    return reference == null ? null : reference.getQualifiedReferenceName();
  }

  @Override
  public @Nullable String getImportedName() {
    if (isOnDemand() || isModule()) return null;

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
  public boolean isModule() {
    GrImportStatementStub stub = getStub();
    return stub != null ? stub.isModule() : findChildByType(GroovyTokenTypes.kMODULE) != null;
  }

  @Override
  public boolean isStatic() {
    GrImportStatementStub stub = getStub();
    return stub != null ? stub.isStatic() : findChildByType(GroovyTokenTypes.kSTATIC) != null;
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
    return stub != null ? stub.isOnDemand() : findChildByType(GroovyTokenTypes.mSTAR) != null;
  }

  @Override
  public @NotNull GrModifierList getAnnotationList() {
    return getStub() != null
           ? Objects.requireNonNull(getStubOrPsiChild(GroovyStubElementTypes.MODIFIER_LIST))
           : findNotNullChildByClass(GrModifierList.class);
  }

  @Override
  public @Nullable PsiClass resolveTargetClass() {
    if (isModule()) return null;
    final GrCodeReferenceElement ref = getImportReference();
    if (ref == null) return null;

    final PsiElement resolved = !isStatic() || isOnDemand() ? ref.resolve() : resolveQualifier();
    return resolved instanceof PsiClass aClass ? aClass : null;
  }

  @Override
  public @Nullable GrImportAlias getAlias() {
    return findChildByClass(GrImportAlias.class);
  }

  @Override
  public @Nullable GroovyImport getImport() {
    String qualifiedName = getImportFqn();
    if (qualifiedName == null) return null;
    if (isModule()) {
      return new ModuleImport(qualifiedName);
    }
    if (isOnDemand()) {
      return isStatic() ? new StaticStarImport(qualifiedName) : new StarImport(qualifiedName);
    }
    String importedName = getImportedName();
    if (importedName == null) return null;
    if (isStatic()) {
      int index = qualifiedName.lastIndexOf('.');
      if (index <= 0) {
        return new RegularImport(qualifiedName, importedName);
      }
      String packageName = qualifiedName.substring(0, index);
      String shortName = qualifiedName.substring(index + 1);
      return new StaticImport(packageName, shortName, importedName);
    }
    return new RegularImport(qualifiedName, importedName);
  }
}
