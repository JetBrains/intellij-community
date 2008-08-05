package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrThrowsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyBaseElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrModifierListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.params.GrParameterListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.JavaIdentifier;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrMethodStub;
import org.jetbrains.plugins.groovy.lang.resolve.MethodTypeInferencer;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author ilyas
 */
public abstract class GrMethodBaseImpl<T extends GrMethodStub> extends GroovyBaseElementImpl<T> implements GrMethod {

  protected GrMethodBaseImpl(final T stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  public GrMethodBaseImpl(final ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitMethod(this);
  }

  public int getTextOffset() {
    return getNameIdentifierGroovy().getTextRange().getStartOffset();
  }

  @NotNull
  public PsiElement getNameIdentifierGroovy() {
    return findChildByType(TokenSets.PROPERTY_NAMES);
  }

  @Nullable
  public GrOpenBlock getBlock() {
    return this.findChildByClass(GrOpenBlock.class);
  }

  public void setBlock(GrCodeBlock newBlock) {
    ASTNode newNode = newBlock.getNode().copyElement();
    final GrOpenBlock oldBlock = getBlock();
    if (oldBlock == null) {
      getNode().addChild(newNode);
      return;
    }
    getNode().replaceChild(oldBlock.getNode(), newNode);
  }

  public GrParameter[] getParameters() {
    GrParameterListImpl parameterList = findChildByClass(GrParameterListImpl.class);
    if (parameterList != null) {
      return parameterList.getParameters();
    }

    return GrParameter.EMPTY_ARRAY;
  }

  public GrTypeElement getReturnTypeElementGroovy() {
    return findChildByClass(GrTypeElement.class);
  }

  @Nullable
  public PsiType getDeclaredReturnType() {
    final GrTypeElement typeElement = getReturnTypeElementGroovy();
    if (typeElement != null) return typeElement.getType();
    return null;
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    for (final GrTypeParameter typeParameter : getTypeParameters()) {
      if (!ResolveUtil.processElement(processor, typeParameter)) return false;
    }

    for (final GrParameter parameter : getParameters()) {
      if (!ResolveUtil.processElement(processor, parameter)) return false;
    }

    return true;
  }

  public GrMember[] getMembers() {
    return new GrMember[]{this};
  }

  private static class MyTypeCalculator implements Function<GrMethod, PsiType> {

    public PsiType fun(GrMethod method) {
      PsiType nominal = getNominalType(method);
      if (nominal != null && nominal.equals(PsiType.VOID)) return nominal;
      PsiType inferred = getInferredType(method);
      if (inferred == null) return nominal;
      return inferred;
    }

    private PsiType getNominalType(GrMethod method) {
      GrTypeElement element = method.getReturnTypeElementGroovy();
      return element != null ? element.getType() : null;
    }

    private PsiType getInferredType(GrMethod method) {
      final GrOpenBlock block = method.getBlock();
      if (block == null) return null;
      return GroovyPsiManager.getInstance(method.getProject()).inferType(method, new MethodTypeInferencer(block));
    }

  }

  private static MyTypeCalculator ourTypesCalculator = new MyTypeCalculator();

  //PsiMethod implementation
  @Nullable
  public PsiType getReturnType() {
    return GroovyPsiManager.getInstance(getProject()).getType(this, ourTypesCalculator);
  }

  @Override
  public Icon getIcon(int flags) {
    return GroovyIcons.METHOD;
  }

  @Override
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      public String getPresentableText() {
        return getName();
      }

      @Nullable
      public String getLocationString() {
        PsiClass clazz = getContainingClass();
        String name = clazz.getQualifiedName();
        assert name != null;
        return "(in " + name + ")";
      }

      @Nullable
      public Icon getIcon(boolean open) {
        return GrMethodBaseImpl.this.getIcon(Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS);
      }

      @Nullable
      public TextAttributesKey getTextAttributesKey() {
        return null;
      }
    };
  }

  @Nullable
  public PsiTypeElement getReturnTypeElement() {
    return null;
  }

  @NotNull
  public GrParameterList getParameterList() {
    GrParameterList parameterList = findChildByClass(GrParameterList.class);
    assert parameterList != null;
    return parameterList;
  }

  @NotNull
  public PsiReferenceList getThrowsList() {
    GrThrowsClause clause = findChildByClass(GrThrowsClause.class);
    assert clause != null;
    return clause;
  }

  @Nullable
  public PsiCodeBlock getBody() {
    return null;
  }

  public boolean isConstructor() {
    return false;
  }

  public boolean isVarArgs() {
    GrParameter[] parameters = getParameters();
    return parameters.length > 0 && parameters[parameters.length - 1].isVarArgs();
  }

  @NotNull
  public MethodSignature getSignature(@NotNull PsiSubstitutor substitutor) {
    return MethodSignatureBackedByPsiMethod.create(this, substitutor);
  }

  @Nullable
  public PsiIdentifier getNameIdentifier() {
    return new JavaIdentifier(getManager(), getContainingFile(), getNameIdentifierGroovy().getTextRange());
  }

  private void findSuperMethodRecursilvely(Set<PsiMethod> methods, PsiClass psiClass, boolean allowStatic,
                                           Set<PsiClass> visited, MethodSignature signature, @NotNull Set<MethodSignature> discoveredSupers) {
    if (psiClass == null) return;
    if (visited.contains(psiClass)) return;
    visited.add(psiClass);
    PsiClassType[] superClassTypes = psiClass.getSuperTypes();

    for (PsiClassType superClassType : superClassTypes) {
      PsiClass resolvedSuperClass = superClassType.resolve();

      if (resolvedSuperClass == null) continue;
      PsiMethod[] superClassMethods = resolvedSuperClass.getMethods();
      final HashSet<MethodSignature> supers = new HashSet<MethodSignature>(3);

      for (PsiMethod superClassMethod : superClassMethods) {
        MethodSignature superMethodSignature = createMethodSignature(superClassMethod);

        if (PsiImplUtil.isExtendsSignature(superMethodSignature, signature) && !dominated(superMethodSignature, discoveredSupers)) {
          if (allowStatic || !superClassMethod.getModifierList().hasExplicitModifier(PsiModifier.STATIC)) {
            methods.add(superClassMethod);
            supers.add(superMethodSignature);
            discoveredSupers.add(superMethodSignature);
          }
        }
      }

      findSuperMethodRecursilvely(methods, resolvedSuperClass, allowStatic, visited, signature, discoveredSupers);
      discoveredSupers.removeAll(supers);
    }
  }

  private boolean dominated(MethodSignature signature, Iterable<MethodSignature> supersInInheritor) {
    for (MethodSignature sig1 : supersInInheritor) {
      if (PsiImplUtil.isExtendsSignature(signature, sig1)) return true;
    }
    return false;
  }

  @NotNull
  public PsiMethod[] findDeepestSuperMethods() {
    List<PsiMethod> methods = new ArrayList<PsiMethod>();
    findDeepestSuperMethodsForClass(methods, this);
    return methods.toArray(PsiMethod.EMPTY_ARRAY);
  }

  private void findDeepestSuperMethodsForClass(List<PsiMethod> collectedMethods, PsiMethod method) {
    PsiClassType[] superClassTypes = method.getContainingClass().getSuperTypes();

    for (PsiClassType superClassType : superClassTypes) {
      PsiClass resolvedSuperClass = superClassType.resolve();

      if (resolvedSuperClass == null) continue;
      PsiMethod[] superClassMethods = resolvedSuperClass.getMethods();

      for (PsiMethod superClassMethod : superClassMethods) {
        MethodSignature superMethodSignature = superClassMethod.getHierarchicalMethodSignature();
        final HierarchicalMethodSignature thisMethodSignature = getHierarchicalMethodSignature();

        if (superMethodSignature.equals(thisMethodSignature) && !superClassMethod.getModifierList().hasExplicitModifier(PsiModifier.STATIC)) {
          checkForMethodOverriding(collectedMethods, superClassMethod);
        }
        findDeepestSuperMethodsForClass(collectedMethods, superClassMethod);
      }
    }
  }

  private void checkForMethodOverriding(List<PsiMethod> collectedMethods, PsiMethod superClassMethod) {
    int i = 0;
    while (i < collectedMethods.size()) {
      PsiMethod collectedMethod = collectedMethods.get(i);
      if (collectedMethod.getContainingClass().equals(superClassMethod.getContainingClass()) || collectedMethod.getContainingClass().isInheritor(superClassMethod.getContainingClass(), true)) {
        collectedMethods.remove(collectedMethod);
        continue;
      }
      i++;
    }
    collectedMethods.add(superClassMethod);
  }

  @NotNull
  public PsiMethod[] findSuperMethods(boolean checkAccess) {
    PsiClass containingClass = getContainingClass();

    Set<PsiMethod> methods = new HashSet<PsiMethod>();
    findSuperMethodRecursilvely(methods, containingClass, false, new HashSet<PsiClass>(), createMethodSignature(this), new HashSet<MethodSignature>());

    return methods.toArray(new PsiMethod[methods.size()]);
  }

  @NotNull
  public PsiMethod[] findSuperMethods(PsiClass parentClass) {
    Set<PsiMethod> methods = new HashSet<PsiMethod>();
    findSuperMethodRecursilvely(methods, parentClass, false, new HashSet<PsiClass>(), createMethodSignature(this), new HashSet<MethodSignature>());
    return methods.toArray(new PsiMethod[methods.size()]);
  }

  @NotNull
  public List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(boolean checkAccess) {
    PsiClass containingClass = getContainingClass();

    Set<PsiMethod> methods = new HashSet<PsiMethod>();
    final MethodSignature signature = createMethodSignature(this);
    findSuperMethodRecursilvely(methods, containingClass, true, new HashSet<PsiClass>(), signature, new HashSet<MethodSignature>());

    List<MethodSignatureBackedByPsiMethod> result = new ArrayList<MethodSignatureBackedByPsiMethod>();
    for (PsiMethod method : methods) {
      result.add(method.getHierarchicalMethodSignature());
    }

    return result;
  }

  public static MethodSignature createMethodSignature(PsiMethod method) {
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    PsiType[] types = new PsiType[parameters.length];
    for (int i = 0; i < types.length; i++) {
      types[i] = parameters[i].getType();
    }
    return MethodSignatureUtil.createMethodSignature(method.getName(), types, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
  }

  @NotNull
  public PsiMethod[] findSuperMethods() {
    PsiClass containingClass = getContainingClass();
    if (containingClass == null) return PsiMethod.EMPTY_ARRAY;

    Set<PsiMethod> methods = new HashSet<PsiMethod>();
    findSuperMethodRecursilvely(methods, containingClass, false, new HashSet<PsiClass>(), createMethodSignature(this), new HashSet<MethodSignature>());

    return methods.toArray(new PsiMethod[methods.size()]);
  }

  /*
  * @deprecated use {@link #findDeepestSuperMethods()} instead
  */

  @Nullable
  public PsiMethod findDeepestSuperMethod() {
    return null;
  }

  @NotNull
  public GrModifierList getModifierList() {
    GrModifierListImpl list = findChildByClass(GrModifierListImpl.class);
    assert list != null;
    return list;
  }

  public boolean hasModifierProperty(@NonNls @NotNull String name) {
    if (name.equals(PsiModifier.ABSTRACT)) {
      final PsiClass containingClass = getContainingClass();
      if (containingClass != null && containingClass.isInterface()) return true;
    }

    return getModifierList().hasModifierProperty(name);
  }

  @NotNull
  public String getName() {
    return PsiImplUtil.getName(this);
  }

  @NotNull
  public HierarchicalMethodSignature getHierarchicalMethodSignature() {
    return PsiSuperMethodImplUtil.getHierarchicalMethodSignature(this);
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(name, getNameIdentifierGroovy());

    return this;
  }

  public boolean hasTypeParameters() {
    return getTypeParameters().length > 0;
  }

  @Nullable
  public GrTypeParameterList getTypeParameterList() {
    return findChildByClass(GrTypeParameterList.class);
  }

  @NotNull
  public GrTypeParameter[] getTypeParameters() {
    final GrTypeParameterList list = getTypeParameterList();
    if (list != null) {
      return list.getTypeParameters();
    }

    return GrTypeParameter.EMPTY_ARRAY;
  }

  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    if (parent instanceof GrTypeDefinitionBody) {
      final PsiElement pparent = parent.getParent();
      if (pparent instanceof PsiClass) {
        return (PsiClass) pparent;
      }
    }


    final PsiFile file = getContainingFile();
    if (file instanceof GroovyFileBase) {
      return ((GroovyFileBase) file).getScriptClass();
    }

    return null;
  }

  @Nullable
  public PsiDocComment getDocComment() {
    return null;
  }

  public boolean isDeprecated() {
    return false;
  }

  @NotNull
  public SearchScope getUseScope() {
    return com.intellij.psi.impl.PsiImplUtil.getMemberUseScope(this);
  }

  public PsiElement getOriginalElement() {
    final PsiClass containingClass = getContainingClass();
    if (containingClass == null) return this;
    PsiClass originalClass = (PsiClass) containingClass.getOriginalElement();
    final PsiMethod originalMethod = originalClass.findMethodBySignature(this, false);
    return originalMethod != null ? originalMethod : this;
  }

  public PsiElement getContext() {
    return getParent();
  }

}
