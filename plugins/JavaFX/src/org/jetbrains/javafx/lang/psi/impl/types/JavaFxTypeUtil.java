package org.jetbrains.javafx.lang.psi.impl.types;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.psi.*;
import org.jetbrains.javafx.lang.psi.impl.JavaFxPsiManagerImpl;
import org.jetbrains.javafx.lang.psi.impl.JavaFxQualifiedName;
import org.jetbrains.javafx.lang.psi.impl.types.java.JavaClassTypeImpl;
import org.jetbrains.javafx.lang.psi.impl.types.java.JavaFunctionTypeImpl;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxClassStub;
import org.jetbrains.javafx.lang.psi.types.JavaFxClassType;

import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxTypeUtil {
  private static final Set<String> PRIMITIVE_TYPES = new HashSet<String>();

  @Nullable
  public static JavaFxClassType getSequenceClassType(final Project project) {
    return (JavaFxClassType)createType(
      JavaFxPsiManagerImpl.getInstance(project).getElementByQualifiedName("com.sun.javafx.runtime.sequence.Sequence"));
  }

  private static class FakeJavaFxClassDefinition extends FakePsiElement implements JavaFxClassDefinition {
    private final JavaFxObjectLiteral myDelegate;

    public FakeJavaFxClassDefinition(JavaFxObjectLiteral delegate) {
      myDelegate = delegate;
    }

    @Override
    public JavaFxElement[] getMembers() {
      return ArrayUtil
        .mergeArrays(myDelegate.getFunctionDefinitions(), myDelegate.getVariableDeclarations(), JavaFxElement.class);
    }

    @Override
    public JavaFxReferenceElement[] getSuperClassElements() {
      return JavaFxReferenceElement.EMPTY_ARRAY;
    }

    @Override
    public JavaFxQualifiedName getQualifiedName() {
      return null;
    }

    @Override
    public IStubElementType getElementType() {
      return null;
    }

    @Override
    public JavaFxClassStub getStub() {
      return null;
    }

    @Override
    public PsiElement getParent() {
      return myDelegate.getParent();
    }

    @Override
    public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                       @NotNull ResolveState state,
                                       PsiElement lastParent,
                                       @NotNull PsiElement place) {
      return myDelegate.processDeclarations(processor, state, lastParent, place);
    }
  }

  static {
    PRIMITIVE_TYPES.add("java.lang.String");
    PRIMITIVE_TYPES.add("java.lang.Integer");
    PRIMITIVE_TYPES.add("java.lang.Double");
    PRIMITIVE_TYPES.add("java.lang.Boolean");
    PRIMITIVE_TYPES.add("javafx.lang.Duration");
  }

  @Nullable
  public static PsiType createType(@Nullable final PsiElement psiElement) {
    if (psiElement instanceof JavaFxVariableDeclaration) {
      return ((JavaFxVariableDeclaration)psiElement).getType();
    }
    if (psiElement instanceof JavaFxParameter) {
      return ((JavaFxParameter)psiElement).getType();
    }
    if (psiElement instanceof JavaFxFunctionDefinition) {
      return new JavaFxFunctionTypeImpl((JavaFxFunction)psiElement);
    }
    if (psiElement instanceof JavaFxClassDefinition) {
      final JavaFxQualifiedName qualifiedName = ((JavaFxClassDefinition)psiElement).getQualifiedName();
      if (qualifiedName != null) {
        final String qualifiedNameString = qualifiedName.toString();
        if (isPrimitiveType(qualifiedNameString)) {
          return getPrimitiveType(qualifiedName.getLastComponent());
        }
      }
      return new JavaFxClassTypeImpl((JavaFxClassDefinition)psiElement);
    }
    if (psiElement instanceof JavaFxObjectLiteral) {
      return new JavaFxClassTypeImpl(new FakeJavaFxClassDefinition((JavaFxObjectLiteral)psiElement));
    }
    if (psiElement instanceof PsiClass) {
      final String qualifiedName = ((PsiClass)psiElement).getQualifiedName();
      if (isPrimitiveType(qualifiedName)) {
        return getPrimitiveType(qualifiedName);
      }
      return new JavaClassTypeImpl((PsiClass)psiElement);
    }
    if (psiElement instanceof PsiField) {
      return ((PsiField)psiElement).getType();
    }
    if (psiElement instanceof PsiMethod) {
      return new JavaFunctionTypeImpl((PsiMethod)psiElement);
    }
    if (psiElement instanceof PsiFile) {
      return new JavaFxFileTypeImpl((PsiFile)psiElement);
    }
    return null;
  }

  @NotNull
  public static PsiType getObjectClassType(final Project project) {
    //noinspection ConstantConditions
    return createType(JavaFxPsiManagerImpl.getInstance(project).getElementByQualifiedName("java.lang.Object"));
  }

  @NotNull
  public static PsiType getKeyValueClassType(final Project project) {
    //noinspection ConstantConditions
    return createType(JavaFxPsiManagerImpl.getInstance(project).getElementByQualifiedName("javafx.animation.KeyValue"));
  }

  private static boolean isPrimitiveType(final String qualifiedName) {
    return PRIMITIVE_TYPES.contains(qualifiedName);
  }

  public static PsiType createSequenceType(final PsiType elementType) {
    return new JavaFxSequenceTypeImpl(elementType);
  }

  private static PsiType getPrimitiveType(final String name) {
    if (name.endsWith("Integer")) {
      return JavaFxPrimitiveType.INTEGER;
    }
    if (name.endsWith("Boolean")) {
      return JavaFxPrimitiveType.BOOLEAN;
    }
    if (name.endsWith("Number")) {
      return JavaFxPrimitiveType.NUMBER;
    }
    if (name.endsWith("Duration")) {
      return JavaFxPrimitiveType.DURATION;
    }
    return JavaFxPrimitiveType.STRING;
  }

  private JavaFxTypeUtil() {
  }
}
