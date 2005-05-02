package com.siyeh.ig.maturity;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.search.TodoItem;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiFile;

public class TodoUtil{
    private TodoUtil(){
        super();
    }

    public static boolean isTodoComment(PsiComment comment){
        final PsiFile file = comment.getContainingFile();
        final PsiManager psiManager = comment.getManager();
        final PsiSearchHelper searchHelper = psiManager.getSearchHelper();
        final TodoItem[] todoItems = searchHelper.findTodoItems(file);
        for(final TodoItem todoItem : todoItems){
            final TextRange commentTextRange = comment.getTextRange();
            final TextRange todoTextRange = todoItem.getTextRange();
            if(commentTextRange.contains(todoTextRange)){
                return true;
            }
        }
        return false;
    }
}