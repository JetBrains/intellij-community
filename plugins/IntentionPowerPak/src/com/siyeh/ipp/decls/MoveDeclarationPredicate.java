package com.siyeh.ipp.decls;

import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MoveDeclarationPredicate implements PsiElementPredicate{
    private static final Class[] TYPES = new Class[]{PsiCodeBlock.class, PsiForStatement.class};

    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiLocalVariable)){
            return false;
        }
        final PsiLocalVariable variable = (PsiLocalVariable) element;
        final PsiExpression initializer = variable.getInitializer();
        if(initializer != null){
            return false;
        }
        final PsiCodeBlock variableBlock =
                PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
        if(variableBlock == null){
            return false;
        }
        final PsiManager manager = variable.getManager();
        final PsiSearchHelper searchHelper = manager.getSearchHelper();
        final PsiReference[] references =
                searchHelper.findReferences(variable, variable.getUseScope(),
                                            false);
        if(references.length == 0){
            return false;
        }
        final PsiElement commonParent = getCommonParent(references);
        if(commonParent == null
                || commonParent instanceof PsiExpressionListStatement){
            return false;
        }
        if(!variableBlock.equals(commonParent)){
            return true;
        }

        final PsiReference firstReference = references[0];
        final PsiElement referenceElement = firstReference.getElement();
        if(referenceElement == null){
            return false;
        }
        final PsiElement child = getChildWhichContainsElement(variableBlock,
                                                              referenceElement);
        if(child == null){
            return false;
        }
        PsiElement prevSibling = child.getPrevSibling();
        if(prevSibling instanceof PsiWhiteSpace){
            prevSibling = prevSibling.getPrevSibling();
        }
        if(prevSibling == null){
            return false;
        }
        return !prevSibling.equals(variable.getParent());
    }

    @Nullable
    public static PsiElement getChildWhichContainsElement(@NotNull PsiElement ancestor,
                                                          @NotNull PsiElement descendant)
    {
        PsiElement element = descendant;
        while(!element.equals(ancestor)){
            descendant = element;
            element = descendant.getParent();
            if(element == null){
                return null;
            }
        }
        return descendant;
    }

    @Nullable
    public static PsiElement getCommonParent(@NotNull PsiReference[] references)
    {
        PsiElement commonParent = null;
        for(PsiReference reference : references){
            final PsiElement referenceElement = reference.getElement();
            final PsiElement parent = getParentOfTypes(referenceElement, TYPES);
            if(commonParent != null && !commonParent.equals(parent)){
                commonParent =
                        PsiTreeUtil.findCommonParent(commonParent, parent);
                commonParent =
                        PsiTreeUtil.getParentOfType(commonParent,
                                                    PsiCodeBlock.class, false);
            } else{
                commonParent = parent;
            }
        }
        if(commonParent instanceof PsiForStatement){
            final PsiForStatement forStatement = (PsiForStatement) commonParent;
            final PsiElement referenceElement = references[0].getElement();
            if(PsiTreeUtil.isAncestor(forStatement.getInitialization(),
                                      referenceElement, true)){
                commonParent = forStatement.getInitialization();
            } else{
                commonParent = PsiTreeUtil.getParentOfType(commonParent,
                                                           PsiCodeBlock.class);
            }
        }

        // common parent may not be a switch() statement to avoid narrowing scope to inside switch
        if(commonParent != null){
            final PsiElement parent = commonParent.getParent();
            if(parent instanceof PsiSwitchStatement && references.length > 1){
                commonParent = PsiTreeUtil.getParentOfType(parent,
                                                           PsiCodeBlock.class,
                                                           false);
            }
        }
        return commonParent;
    }

    @Nullable
    private static PsiElement getParentOfTypes(@Nullable PsiElement element,
                                               @NotNull Class[] classes){
        if(element == null) return null;

        while(element != null){
            for(Class clazz : classes){
                if(clazz.isInstance(element)){
                    return element;
                }
            }
            element = element.getParent();
        }
        return null;
    }
}
