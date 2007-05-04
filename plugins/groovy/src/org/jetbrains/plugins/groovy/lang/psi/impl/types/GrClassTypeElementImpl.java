package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClassTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeOrPackageReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.04.2007
 */
public class GrClassTypeElementImpl extends GroovyPsiElementImpl implements GrClassTypeElement {
  public GrClassTypeElementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Type element";
  }

  @NotNull
  public GrTypeOrPackageReferenceElement getReferenceElement() {
    return findChildByClass(GrTypeOrPackageReferenceElement.class);
  }

  @NotNull
  public PsiType getType() {
    return new MyClassType();
  }

  private class MyClassType extends PsiClassType {

    @Nullable
    public PsiClass resolve() {
      GrTypeOrPackageReferenceElement refElement = getReferenceElement();
      PsiElement resolved = refElement.resolve();
      return resolved instanceof PsiClass ? (PsiClass) resolved : null;
    }

    public String getClassName() {
      return getReferenceElement().getReferenceName();
    }

    @NotNull
    public PsiType[] getParameters() {
      return PsiType.EMPTY_ARRAY;
    }

    @NotNull
      public ClassResolveResult resolveGenerics() {
      return new ClassResolveResult() {
        public PsiClass getElement() {
          return resolve();
        }

        public PsiSubstitutor getSubstitutor() {
          return PsiSubstitutor.EMPTY;
        }

        public boolean isPackagePrefixPackageReference() {
          return false;
        }

        public boolean isAccessible() {
          return true; //TODO
        }

        public boolean isStaticsScopeCorrect() {
          return true; //TODO
        }

        public PsiElement getCurrentFileResolveScope() {
          return null; //TODO???
        }

        public boolean isValidResult() {
          return isStaticsScopeCorrect() && isAccessible();
        }
      };
    }

    @NotNull
      public PsiClassType rawType() {
      return this;
    }

    public String getPresentableText() {
      return getReferenceElement().getReferenceName();
    }

    @NonNls
    public String getCanonicalText() {
      PsiClass resolved = resolve();
      return resolved == null ? null : resolved.getQualifiedName();
    }

    public String getInternalCanonicalText() {
      return getCanonicalText();
    }

    public boolean isValid() {
      return GrClassTypeElementImpl.this.isValid();
    }

    public boolean equalsToText(@NonNls String text) {
      return text.endsWith(getPresentableText()) && //optimization
          text.equals(getCanonicalText());
    }

    @NotNull
    public GlobalSearchScope getResolveScope() {
      return GrClassTypeElementImpl.this.getResolveScope();
    }

    @NotNull
      public LanguageLevel getLanguageLevel() {
      return myLanguageLevel;
    }

    public PsiClassType setLanguageLevel(final LanguageLevel languageLevel) {
      MyClassType copy = new MyClassType();
      copy.myLanguageLevel = languageLevel;
      return copy;
    }
  }
}
