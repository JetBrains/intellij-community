package org.jetbrains.plugins.gant.reference;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.ResolveResult;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gant.GantIcons;
import org.jetbrains.plugins.gant.util.GantUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ilyas
 */
public class GantTargetReference implements PsiPolyVariantReference {

  @NotNull
  private final GrReferenceExpression myRefExpr;

  public GantTargetReference(@NotNull GrReferenceExpression refExpr) {
    myRefExpr = refExpr;
  }

  @NotNull
  public ResolveResult[] multiResolve(boolean incompleteCode) {
    if (myRefExpr.getQualifierExpression() != null) return ResolveResult.EMPTY_ARRAY;
    PsiFile file = myRefExpr.getContainingFile();
    if (!GantUtils.isGantScriptFile(file)) return ResolveResult.EMPTY_ARRAY;

    ArrayList<ResolveResult> res = new ArrayList<ResolveResult>();
    for (GrArgumentLabel label : GantUtils.getScriptTargets((GroovyFile)file)) {
      if (label.getName().equals(myRefExpr.getName())) {
        res.add(new GroovyResolveResultImpl(label, true));
      }
    }
    return res.toArray(new ResolveResult[res.size()]);
  }

  @NotNull
  public GrReferenceExpression getElement() {
    return myRefExpr;
  }

  public TextRange getRangeInElement() {
    return getElement().getRangeInElement();
  }

  @Nullable
  public PsiElement resolve() {
    ResolveResult[] results = multiResolve(false);
    if (results.length == 1) return results[0].getElement();
    return null;
  }

  public String getCanonicalText() {
    return myRefExpr.getCanonicalText();
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return myRefExpr.setName(newElementName);
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  public boolean isReferenceTo(PsiElement element) {
    return false;
  }

  public LookupElement[] getScriptTargets() {
    List<LookupElement> pageProperties = new ArrayList<LookupElement>();
    final LookupElementFactory factory = LookupElementFactory.getInstance();
    PsiFile file = myRefExpr.getContainingFile();
    if (GantUtils.isGantScriptFile(file)) {
      for (GrArgumentLabel label : GantUtils.getScriptTargets(((GroovyFile)file))) {
        String name = label.getName();
        pageProperties.add(factory.createLookupElement(name).setIcon(GantIcons.GANT_TARGET));
      }
    }
    return pageProperties.toArray(new LookupElement[pageProperties.size()]);
  }


  public Object[] getVariants() {
    GrExpression qualifier = myRefExpr.getQualifierExpression();
    if (qualifier == null) {
      return getScriptTargets();
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public boolean isSoft() {
    return false;
  }

}


