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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.ui.RowIcon;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityIcons;
import com.intellij.util.containers.ContainerUtil;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl.GrDocCommentUtil;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrStubElementBase;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeDefinitionStub;
import org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ast.AstTransformContributor;
import org.jetbrains.plugins.groovy.runner.GroovyRunnerUtil;
import org.jetbrains.plugins.groovy.util.LightCacheKey;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil.*;

/**
 * @author ilyas
 */
public abstract class GrTypeDefinitionImpl extends GrStubElementBase<GrTypeDefinitionStub> implements GrTypeDefinition, StubBasedPsiElement<GrTypeDefinitionStub> {

  private static final LightCacheKey<List<GrField>> AST_TRANSFORM_FIELD = LightCacheKey.createByJavaModificationCount();
  private static final RecursionGuard ourGuard = RecursionManager.createGuard("groovyMembers");

  private volatile PsiClass[] myInnerClasses;
  private volatile GrMethod[] myGroovyMethods;
  private volatile PsiMethod[] myConstructors;
  private volatile GrMethod[] myCodeConstructors;

  Key<CachedValue<PsiMethod[]>> CACHED_METHODS = Key.create("cached.type.definition.methods");

  public GrTypeDefinitionImpl(@NotNull ASTNode node) {
    super(node);
  }

  protected GrTypeDefinitionImpl(GrTypeDefinitionStub stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  @Override
  public PsiElement getParent() {
    return getDefinitionParent();
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitTypeDefinition(this);
  }

  public int getTextOffset() {
    return getNameIdentifierGroovy().getTextRange().getStartOffset();
  }

  @Nullable
  public String getQualifiedName() {
    final GrTypeDefinitionStub stub = getStub();
    if (stub != null) {
      return stub.getQualifiedName();
    }

    PsiElement parent = getParent();
    if (parent instanceof GroovyFile) {
      String packageName = ((GroovyFile)parent).getPackageName();
      return packageName.length() > 0 ? packageName + "." + getName() : getName();
    }

    final PsiClass containingClass = getContainingClass();
    if (containingClass != null && containingClass.getQualifiedName() != null) {
      return containingClass.getQualifiedName() + "." + getName();
    }

    return null;
  }

  @Nullable
  public GrTypeDefinitionBody getBody() {
    return getStubOrPsiChild(GroovyElementTypes.CLASS_BODY);
  }

  @NotNull
  public GrMembersDeclaration[] getMemberDeclarations() {
    GrTypeDefinitionBody body = getBody();
    if (body == null) return GrMembersDeclaration.EMPTY_ARRAY;
    return body.getMemberDeclarations();
  }

  public ItemPresentation getPresentation() {
    return ItemPresentationProviders.getItemPresentation(this);
  }

  @Nullable
  public GrExtendsClause getExtendsClause() {
    return getStubOrPsiChild(GroovyElementTypes.EXTENDS_CLAUSE);
  }

  @Nullable
  public GrImplementsClause getImplementsClause() {
    return getStubOrPsiChild(GroovyElementTypes.IMPLEMENTS_CLAUSE);
  }

  public String[] getSuperClassNames() {
    final GrTypeDefinitionStub stub = getStub();
    if (stub != null) {
      return stub.getSuperClassNames();
    }
    return ArrayUtil.mergeArrays(getExtendsNames(), getImplementsNames());
  }

  protected String[] getImplementsNames() {
    GrImplementsClause implementsClause = getImplementsClause();
    GrCodeReferenceElement[] implementsRefs =
      implementsClause != null ? implementsClause.getReferenceElementsGroovy() : GrCodeReferenceElement.EMPTY_ARRAY;
    ArrayList<String> implementsNames = new ArrayList<String>(implementsRefs.length);
    for (GrCodeReferenceElement ref : implementsRefs) {
      String name = ref.getReferenceName();
      if (name != null) implementsNames.add(name);
    }

    return ArrayUtil.toStringArray(implementsNames);
  }

  protected String[] getExtendsNames() {
    GrExtendsClause extendsClause = getExtendsClause();
    GrCodeReferenceElement[] extendsRefs =
      extendsClause != null ? extendsClause.getReferenceElementsGroovy() : GrCodeReferenceElement.EMPTY_ARRAY;
    ArrayList<String> extendsNames = new ArrayList<String>(extendsRefs.length);
    for (GrCodeReferenceElement ref : extendsRefs) {
      String name = ref.getReferenceName();
      if (name != null) extendsNames.add(name);
    }
    return ArrayUtil.toStringArray(extendsNames);
  }

  @NotNull
  public PsiElement getNameIdentifierGroovy() {
    PsiElement result = findChildByType(TokenSets.PROPERTY_NAMES);
    assert result != null;
    return result;
  }

  public void checkDelete() throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
  }

  public void delete() throws IncorrectOperationException {
    PsiElement parent = getParent();
    if (parent instanceof GroovyFileImpl) {
      GroovyFileImpl file = (GroovyFileImpl)parent;
      if (file.getTypeDefinitions().length == 1 && !file.isScript()) {
        file.delete();
        return;
      }
    }

    super.delete();
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return GrClassImplUtil.processDeclarations(this, processor, state, lastParent, place);
  }

  public String getName() {
    final GrTypeDefinitionStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }
    return PsiImplUtil.getName(this);
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return isClassEquivalentTo(this, another);
  }

  public boolean isInterface() {
    return false;
  }

  public boolean isAnnotationType() {
    return false;
  }

  public boolean isEnum() {
    return false;
  }

  @Nullable
  public PsiReferenceList getExtendsList() {
    //return PsiImplUtil.getOrCreatePsiReferenceList(getExtendsClause(), PsiReferenceList.Role.EXTENDS_LIST);
    return getExtendsClause();
  }

  @Nullable
  public PsiReferenceList getImplementsList() {
    //return PsiImplUtil.getOrCreatePsiReferenceList(getImplementsClause(), PsiReferenceList.Role.IMPLEMENTS_LIST);
    return getImplementsClause();
  }

  @NotNull
  public PsiClassType[] getExtendsListTypes() {
    return CachedValuesManager.getCachedValue(this, new CachedValueProvider<PsiClassType[]>() {
      @Override
      public Result<PsiClassType[]> compute() {
        return Result.create(GrClassImplUtil.getExtendsListTypes(GrTypeDefinitionImpl.this), PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
      }
    });
  }

  @NotNull
  public PsiClassType[] getImplementsListTypes() {
    return CachedValuesManager.getCachedValue(this, new CachedValueProvider<PsiClassType[]>() {
      @Override
      public Result<PsiClassType[]> compute() {
        return Result.create(GrClassImplUtil.getImplementsListTypes(GrTypeDefinitionImpl.this),
                             PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
      }
    });
  }

  @Nullable
  public PsiClass getSuperClass() {
    return GrClassImplUtil.getSuperClass(this);
  }

  public PsiClass[] getInterfaces() {
    return CachedValuesManager.getCachedValue(this, new CachedValueProvider<PsiClass[]>() {
      @Override
      public Result<PsiClass[]> compute() {
        return Result
          .create(GrClassImplUtil.getInterfaces(GrTypeDefinitionImpl.this), PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
      }
    });
  }

  @NotNull
  public final PsiClass[] getSupers() {
    return GrClassImplUtil.getSupers(this);
  }

  @NotNull
  public PsiClassType[] getSuperTypes() {
    return GrClassImplUtil.getSuperTypes(this);
  }

  @NotNull
  public GrField[] getCodeFields() {
    GrTypeDefinitionBody body = getBody();
    if (body != null) {
      return body.getFields();
    }

    return GrField.EMPTY_ARRAY;
  }

  @Override
  public PsiField findCodeFieldByName(String name, boolean checkBases) {
    return GrClassImplUtil.findFieldByName(this, name, checkBases, false);
  }

  private List<GrField> getSyntheticFields() {
    List<GrField> fields = AST_TRANSFORM_FIELD.getCachedValue(this);
    if (fields == null) {
      final RecursionGuard.StackStamp stamp = ourGuard.markStack();
      fields = AstTransformContributor.runContributorsForFields(this);
      if (stamp.mayCacheNow()) {
        fields = AST_TRANSFORM_FIELD.putCachedValue(this, fields);
      }
    }

    return fields;
  }

  @NotNull
  public GrField[] getFields() {
    GrField[] codeFields = getCodeFields();

    List<GrField> fromAstTransform = getSyntheticFields();
    if (fromAstTransform.isEmpty()) return codeFields;

    GrField[] res = new GrField[codeFields.length + fromAstTransform.size()];
    System.arraycopy(codeFields, 0, res, 0, codeFields.length);

    for (int i = 0; i < fromAstTransform.size(); i++) {
      res[codeFields.length + i] = fromAstTransform.get(i);
    }

    return res;
  }

  @NotNull
  public PsiMethod[] getMethods() {
    CachedValue<PsiMethod[]> cached = getUserData(CACHED_METHODS);
    if (cached == null) {
      cached = CachedValuesManager.getManager(getProject()).createCachedValue(new CachedValueProvider<PsiMethod[]>() {
        @Override
        public Result<PsiMethod[]> compute() {
          GrTypeDefinitionBody body = getBody();
          List<PsiMethod> result = new ArrayList<PsiMethod>();
          if (body != null) {
            collectMethodsFromBody(body, result);
          }

          for (PsiMethod method : AstTransformContributor.runContributorsForMethods(GrTypeDefinitionImpl.this)) {
            addExpandingReflectedMethods(result, method);
          }

          for (GrField field : getSyntheticFields()) {
            ContainerUtil.addIfNotNull(result, field.getSetter());
            Collections.addAll(result, field.getGetters());
          }
          return Result.create(result.toArray(new PsiMethod[result.size()]), PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
        }
      }, false);
      putUserData(CACHED_METHODS, cached);
    }


    return cached.getValue();
  }

  @NotNull
  public GrMethod[] getCodeMethods() {
    GrMethod[] cached = myGroovyMethods;
    if (cached == null) {
      RecursionGuard.StackStamp stamp = ourGuard.markStack();
      GrTypeDefinitionBody body = getBody();
      cached = body != null ? body.getMethods() : GrMethod.EMPTY_ARRAY;
      if (stamp.mayCacheNow()) {
        myGroovyMethods = cached;
      }
    }
    return cached;
  }

  public void subtreeChanged() {
    myInnerClasses = null;
    myConstructors = null;
    myCodeConstructors = null;
    myGroovyMethods = null;
    super.subtreeChanged();
  }

  @NotNull
  public PsiMethod[] getConstructors() {
    PsiMethod[] cached = myConstructors;
    if (cached == null) {
      RecursionGuard.StackStamp stamp = ourGuard.markStack();
      List<PsiMethod> result = new ArrayList<PsiMethod>();
      for (final PsiMethod method : getMethods()) {
        if (method.isConstructor()) {
          addExpandingReflectedMethods(result, method);
        }
      }

      cached = result.toArray(new PsiMethod[result.size()]);
      if (stamp.mayCacheNow()) {
        myConstructors = cached;
      }
    }
    return cached;
  }

  @NotNull
  public GrMethod[] getCodeConstructors() {
    GrMethod[] cached = myCodeConstructors;
    if (cached == null) {
      RecursionGuard.StackStamp stamp = ourGuard.markStack();
      List<GrMethod> result = new ArrayList<GrMethod>();
      for (final GrMethod method : getCodeMethods()) {
        if (method.isConstructor()) {
          result.add(method);
        }
      }

      cached = result.toArray(new GrMethod[result.size()]);
      if (stamp.mayCacheNow()) {
        myCodeConstructors = cached;
      }
    }
    return cached;
  }

  @NotNull
  public PsiClass[] getInnerClasses() {
    PsiClass[] inners = myInnerClasses;
    if (inners == null) {
      RecursionGuard.StackStamp stamp = ourGuard.markStack();
      final GrTypeDefinitionBody body = getBody();
      inners = body != null ? body.getInnerClasses() : PsiClass.EMPTY_ARRAY;
      if (stamp.mayCacheNow()) {
        myInnerClasses = inners;
      }
    }

    return inners;
  }

  @NotNull
  public GrClassInitializer[] getInitializers() {
    GrTypeDefinitionBody body = getBody();
    if (body == null) return GrClassInitializer.EMPTY_ARRAY;

    return body.getInitializers();
  }

  @NotNull
  public PsiField[] getAllFields() {
    return GrClassImplUtil.getAllFields(this);
  }

  @NotNull
  public PsiMethod[] getAllMethods() {
    return GrClassImplUtil.getAllMethods(this);
  }

  @NotNull
  public PsiClass[] getAllInnerClasses() {
    return PsiClassImplUtil.getAllInnerClasses(this);
  }

  @Nullable
  public PsiField findFieldByName(String name, boolean checkBases) {
    return GrClassImplUtil.findFieldByName(this, name, checkBases, true);
  }

  @Nullable
  public PsiMethod findMethodBySignature(PsiMethod patternMethod, boolean checkBases) {
    return GrClassImplUtil.findMethodBySignature(this, patternMethod, checkBases);
  }

  @NotNull
  public PsiMethod[] findMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
    return GrClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases);
  }

  @NotNull
  public PsiMethod[] findCodeMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
    return GrClassImplUtil.findCodeMethodsBySignature(this, patternMethod, checkBases);
  }

  @NotNull
  public PsiMethod[] findMethodsByName(@NonNls String name, boolean checkBases) {
    return GrClassImplUtil.findMethodsByName(this, name, checkBases);
  }

  @NotNull
  public PsiMethod[] findCodeMethodsByName(@NonNls String name, boolean checkBases) {
    return GrClassImplUtil.findCodeMethodsByName(this, name, checkBases);
  }

  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(String name, boolean checkBases) {
    return GrClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases);
  }

  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
    return GrClassImplUtil.getAllMethodsAndTheirSubstitutors(this);
  }

  @Nullable
  public PsiClass findInnerClassByName(String name, boolean checkBases) {
    return GrClassImplUtil.findInnerClassByName(this, name, checkBases);
  }

  @Nullable
  public PsiElement getLBrace() {
    final GrTypeDefinitionBody body = getBody();
    return body == null ? null : body.getLBrace();
  }

  @Nullable
  public PsiElement getRBrace() {
    final GrTypeDefinitionBody body = getBody();
    return body == null ? null : body.getRBrace();
  }

  public boolean isAnonymous() {
    return false;
  }

  @Nullable
  public PsiIdentifier getNameIdentifier() {
    return PsiUtil.getJavaNameIdentifier(this);
  }

  @Nullable
  public PsiElement getScope() {
    final GrTypeDefinitionStub stub = getStub();
    if (stub != null) {
      return stub.getParentStub().getPsi();
    }

    ASTNode treeElement = getNode();
    ASTNode parent = treeElement.getTreeParent();

    while (parent != null) {
      if (parent.getElementType() instanceof IStubElementType && !(parent.getElementType() == GroovyElementTypes.CLASS_BODY)) {
        return parent.getPsi();
      }
      parent = parent.getTreeParent();
    }

    return getContainingFile();
  }

  public boolean isInheritor(@NotNull PsiClass baseClass, boolean checkDeep) {
    return InheritanceImplUtil.isInheritor(this, baseClass, checkDeep);
  }

  public boolean isInheritorDeep(PsiClass baseClass, @Nullable PsiClass classToByPass) {
    return InheritanceImplUtil.isInheritorDeep(this, baseClass, classToByPass);
  }

  @Nullable
  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    if (parent instanceof GrTypeDefinitionBody) {
      final PsiElement pparent = parent.getParent();
      if (pparent instanceof PsiClass) {
        return (PsiClass)pparent;
      }
    }

    return null;
  }

  @NotNull
  public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return PsiSuperMethodImplUtil.getVisibleSignatures(this);
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    boolean renameFile = isRenameFileOnClassRenaming();

    final String oldName = getName();
    PsiImplUtil.setName(name, getNameIdentifierGroovy());

    final GrTypeDefinitionBody body = getBody();
    if (body != null) {
      for (PsiMethod method : body.getMethods()) {
        if (method.isConstructor() && method.getName().equals(oldName)) method.setName(name);
      }
    }

    if (renameFile) {
      final PsiFile file = getContainingFile();
      final VirtualFile virtualFile = file.getVirtualFile();
      final String ext;
      if (virtualFile != null) {
        ext = virtualFile.getExtension();
      } else {
        ext = GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension();
      }
      file.setName(name + "." + ext);
    }

    return this;
  }

  @Nullable
  public GrModifierList getModifierList() {
    return getStubOrPsiChild(GroovyElementTypes.MODIFIERS);
  }

  public boolean hasModifierProperty(@NonNls @NotNull String name) {
    PsiModifierList modifierList = getModifierList();
    return modifierList != null && modifierList.hasModifierProperty(name);
  }

  @Nullable
  public GrDocComment getDocComment() {
    return GrDocCommentUtil.findDocComment(this);
  }

  public boolean isDeprecated() {
    final GrTypeDefinitionStub stub = getStub();
    if (stub != null) {
      return stub.isDeprecatedByDoc() || com.intellij.psi.impl.PsiImplUtil.isDeprecatedByAnnotation(this);
    }
    return com.intellij.psi.impl.PsiImplUtil.isDeprecatedByDocTag(this) || com.intellij.psi.impl.PsiImplUtil.isDeprecatedByAnnotation(this);
  }

  public boolean hasTypeParameters() {
    return getTypeParameters().length > 0;
  }

  @Nullable
  public GrTypeParameterList getTypeParameterList() {
    return getStubOrPsiChild(GroovyElementTypes.TYPE_PARAMETER_LIST);
  }

  @NotNull
  public GrTypeParameter[] getTypeParameters() {
    final GrTypeParameterList list = getTypeParameterList();
    if (list != null) {
      return list.getTypeParameters();
    }

    return GrTypeParameter.EMPTY_ARRAY;
  }

  @Override
  protected boolean isVisibilitySupported() {
    return true;
  }

  @Nullable
  public Icon getIcon(int flags) {
    Icon icon = getIconInner();
    final boolean isLocked = (flags & ICON_FLAG_READ_STATUS) != 0 && !isWritable();
    RowIcon rowIcon = createLayeredIcon(this, icon, ElementPresentationUtil.getFlags(this, isLocked) | getFlagsInner());
    if ((flags & ICON_FLAG_VISIBILITY) != 0) {
      VisibilityIcons.setVisibilityIcon(getModifierList(), rowIcon);
    }
    return rowIcon;
  }

  //hack to get runnable icon for all classes that can be run by Groovy
  private int getFlagsInner() {
    return GroovyRunnerUtil.isRunnable(this) ? ElementPresentationUtil.FLAGS_RUNNABLE : 0;
  }

  private Icon getIconInner() {
    if (isAnnotationType()) return JetgroovyIcons.Groovy.AnnotationType;

    if (isInterface()) return JetgroovyIcons.Groovy.Interface;

    if (isEnum()) return JetgroovyIcons.Groovy.Enum;

    if (hasModifierProperty(PsiModifier.ABSTRACT)) return JetgroovyIcons.Groovy.AbstractClass;

    return JetgroovyIcons.Groovy.Class;
  }

  private boolean isRenameFileOnClassRenaming() {
    final PsiFile file = getContainingFile();
    if (!(file instanceof GroovyFile)) return false;
    final GroovyFile groovyFile = (GroovyFile)file;
    if (groovyFile.isScript()) return false;
    final String name = getName();
    final VirtualFile vFile = groovyFile.getVirtualFile();
    return vFile != null && name != null && name.equals(vFile.getNameWithoutExtension());
  }

  @Nullable
  public PsiElement getOriginalElement() {
    return PsiImplUtil.getOriginalElement(this, getContainingFile());
  }

  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    if (anchor == null) {
      return add(element);
    }
    final GrTypeDefinitionBody body = getBody();
    if (anchor.getParent() == body) {

      final PsiElement nextChild = anchor.getNextSibling();
      if (nextChild == null) {
        return add(element);
      }

      if (body == null) throw new IncorrectOperationException("Class must have body");
      return body.addBefore(element, nextChild);
    }
    else {
      return super.addAfter(element, anchor);
    }
  }

  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    if (anchor == null) {
      return add(element);
    }

    final GrTypeDefinitionBody body = getBody();
    if (anchor.getParent() != body) {
      return super.addBefore(element, anchor);
    }

    if (body == null) throw new IncorrectOperationException("Class must have body");
    return body.addBefore(element, anchor);
  }

  public PsiElement add(@NotNull PsiElement psiElement) throws IncorrectOperationException {
    final GrTypeDefinitionBody body = getBody();

    if (body == null) throw new IncorrectOperationException("Class must have body");

    final PsiElement lBrace = body.getLBrace();

    if (lBrace == null) throw new IncorrectOperationException("No left brace");

    PsiMember member = getAnyMember(psiElement);
    PsiElement anchor = member != null ? getDefaultAnchor(body, member) : null;
    if (anchor == null) {
      anchor = lBrace.getNextSibling();
    }

    if (anchor != null) {
      ASTNode node = anchor.getNode();
      assert node != null;
      if (GroovyTokenTypes.mSEMI.equals(node.getElementType())) {
        anchor = anchor.getNextSibling();
      }
      if (psiElement instanceof GrField) {
        //add field with modifiers which are in its parent
        int i = ArrayUtilRt.find(((GrVariableDeclaration)psiElement.getParent()).getVariables(), psiElement);
        psiElement = body.addBefore(psiElement.getParent(), anchor);
        GrVariable[] vars = ((GrVariableDeclaration)psiElement).getVariables();
        for (int j = 0; j < vars.length; j++) {
          if (i != j) vars[i].delete();
        }
        psiElement = vars[i];
      }
      else {
        psiElement = body.addBefore(psiElement, anchor);
      }
    }
    else {
      psiElement = body.add(psiElement);
    }

    return psiElement;
  }

  @Nullable
  private static PsiMember getAnyMember(@Nullable PsiElement psiElement) {
    if (psiElement instanceof PsiMember) {
      return (PsiMember)psiElement;
    }
    if (psiElement instanceof GrVariableDeclaration) {
      final GrMember[] members = ((GrVariableDeclaration)psiElement).getMembers();
      if (members.length > 0) {
        return members[0];
      }
    }
    return null;
  }

  @Nullable
  private PsiElement getDefaultAnchor(GrTypeDefinitionBody body, PsiMember member) {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());

    int order = JavaPsiImplementationHelperImpl.getMemberOrderWeight(member, settings);
    if (order < 0) return null;

    PsiElement lastMember = null;
    for (PsiElement child = body.getFirstChild(); child != null; child = child.getNextSibling()) {
      int order1 = JavaPsiImplementationHelperImpl.getMemberOrderWeight(getAnyMember(child), settings);
      if (order1 < 0) continue;
      if (order1 > order) {
        final PsiElement lBrace = body.getLBrace();
        if (lastMember != null) {
          PsiElement nextSibling = lastMember.getNextSibling();
          while (nextSibling instanceof LeafPsiElement && (nextSibling.getText().equals(",") || nextSibling.getText().equals(";"))) {
            nextSibling = nextSibling.getNextSibling();
          }
          return nextSibling == null && lBrace != null ? PsiUtil.skipWhitespacesAndComments(lBrace.getNextSibling(), true) : nextSibling;
        }
        else if (lBrace != null) {
          return PsiUtil.skipWhitespacesAndComments(lBrace.getNextSibling(), true);
        }
      }
      lastMember = child;
    }
    return body.getRBrace();
  }
}
