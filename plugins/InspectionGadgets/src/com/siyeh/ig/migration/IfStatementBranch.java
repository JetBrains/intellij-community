/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.migration;

import com.intellij.psi.*;

import java.util.*;

class IfStatementBranch {

    private final Set<String> topLevelVariables = new HashSet<String>(3);
    private final LinkedList<String> comments = new LinkedList<String>();
    private final LinkedList<String> statementComments = new LinkedList<String>();
    private final List<PsiExpression> conditions = new ArrayList<PsiExpression>(3);
    private final PsiStatement statement;
    private final boolean elseBranch;

    public IfStatementBranch(PsiStatement branch, boolean elseBranch) {
        statement = branch;
        this.elseBranch = elseBranch;
        calculateVariablesDeclared(statement);
    }

    public void addComment(String comment) {
        comments.addFirst(comment);
    }

    public void addStatementComment(String comment) {
        statementComments.addFirst(comment);
    }

    public void addCaseExpression(PsiExpression expression) {
        conditions.add(expression);
    }

    public PsiStatement getStatement() {
        return statement;
    }

    public List<PsiExpression> getConditions() {
        return Collections.unmodifiableList(conditions);
    }

    public boolean isElse() {
        return elseBranch;
    }

    public boolean topLevelDeclarationsConflictWith(
            IfStatementBranch testBranch) {
        final Set<String> topLevel = testBranch.topLevelVariables;
        return intersects(topLevelVariables, topLevel);
    }

    private static boolean intersects(Set<String> set1,
                                      Set<String> set2) {
        for (final String s : set1) {
            if (set2.contains(s)) {
                return true;
            }
        }
        return false;
    }

    public List<String> getComments() {
        return comments;
    }

    public List<String> getStatementComments() {
        return statementComments;
    }

    public void calculateVariablesDeclared(PsiStatement statement) {
        if (statement == null) {
            return;
        }
        if (statement instanceof PsiDeclarationStatement) {
            final PsiDeclarationStatement declarationStatement =
                    (PsiDeclarationStatement) statement;
            final PsiElement[] elements =
                    declarationStatement.getDeclaredElements();
            for (PsiElement element : elements) {
                final PsiVariable variable = (PsiVariable) element;
                final String varName = variable.getName();
                topLevelVariables.add(varName);
            }
        } else if (statement instanceof PsiBlockStatement) {
            final PsiBlockStatement block = (PsiBlockStatement) statement;
            final PsiCodeBlock codeBlock = block.getCodeBlock();
            final PsiStatement[] statements = codeBlock.getStatements();
            for (PsiStatement statement1 : statements) {
                calculateVariablesDeclared(statement1);
            }
        }
    }
}
