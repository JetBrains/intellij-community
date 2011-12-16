/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.refactoring.rename.naming;

import junit.framework.TestCase;

/**
 * @author dsl
 */
public class NameSuggesterTest extends TestCase {
  public void testChanges1() throws Exception {
    doChangesTest("NameSuggesterTest", "NameUnifierTest", "[<Suggester,Unifier>]");
  }

  public void testChanges2() throws Exception {
    doChangesTest("CompletionContext", "CompletionResult", "[<Context,Result>]");
  }

  public void testChanges3() throws Exception {
    doChangesTest("CodeCompletionContext", "CompletionResult", "[<Code,>, <Context,Result>]");
  }

  public void testChanges4() throws Exception {
    doChangesTest("A", "B", "[<A,B>]");
  }

  public void testChanges5() throws Exception {
    doChangesTest("PsiManager", "PsiManagerImpl", "[<,Impl>]");
  }

  public void testChanges6() throws Exception {
    doChangesTest("IBase", "IMain", "[<Base,Main>]");
  }

  public void testSuggestions1() throws Exception {
    doSuggestionTest("NameSuggesterTest", "NameUnifierTest", "suggester", "unifier");
  }

  public void testSuggestions2() throws Exception {
    doSuggestionTest("CompletionContext", "CompletionResult", "completionContext", "completionResult");
  }

  public void testSuggestions3() throws Exception {
    doSuggestionTest("CompletionContext", "CompletionResult", "context", "result");
  }


  public void testSuggestions4() throws Exception {
    doSuggestionTest("CompletionContext", "CompletionResult", "codeCompletionContext", "codeCompletionResult");
  }

  public void testSuggestions5() throws Exception {
    doSuggestionTest("CodeCompletionContext", "CompletionResult", "codeCompletionContext", "completionResult");
  }

  public void testSuggestions6() throws Exception {
    doSuggestionTest("CodeCompletionContext", "CompletionResult", "context", "result");
  }

  public void testSuggestions7() throws Exception {
    doSuggestionTest("CodeCompletionContext", "CompletionResult", "contextWithAdvances", "resultWithAdvances");
  }

  public void testSuggestions8() throws Exception {
    doSuggestionTest("CodeCompletionContext", "CompletionResult", "_context", "_result");
  }

  public void testSuggestions9() throws Exception {
    doSuggestionTest("CodeCompletionContext", "CompletionResult", "p_context", "p_result");
  }

  public void testSuggestions10() throws Exception {
    doSuggestionTest("IBase", "IMain", "base", "main");
  }

  public void testSuggestions11() throws Exception {
    doSuggestionTest("Descriptor", "BasicDescriptor", "descriptor", "basicDescriptor");
  }

  public void testSuggestions12() throws Exception {
    doSuggestionTest("Provider", "ObjectProvider", "provider", "objectProvider");
  }

  public void testSuggestions13() throws Exception {
    doSuggestionTest("SimpleModel", "Model", "simpleModel", "model");
  }


  public void testSuggestions14() throws Exception {
    doSuggestionTest("ConfigItem", "Config", "item", "item");
  }

  public void testSuggestions15() throws Exception {
    doSuggestionTest("Transaction", "TransactionPolicy", "transaction", "transactionPolicy");
  }

  public void testSuggestions16() throws Exception {
    doSuggestionTest("Transaction", "TransactionPolicyHandler", "transaction", "transactionPolicyHandler");
  }

  public void testSuggestions17() throws Exception {
    doSuggestionTest("Transaction", "StrictTransactionPolicyHandler", "transaction", "strictTransactionPolicyHandler");
  }

  public void testSuggestions18() throws Exception {
    doSuggestionTest("Foo", "FooTask", "fooImpl", "fooTaskImpl");
  }

  public void testSuggestions19() throws Exception {
    doSuggestionTest("Foo", "FooTask", "implFoo", "implFooTask");
  }

  public void testSuggestions20() throws Exception {
    doSuggestionTest("Foo", "TaskFoo", "fooImpl", "taskFooImpl");
  }

   public void testSuggestions21() throws Exception {
    doSuggestionTest("Foo", "TaskFoo", "implFoo", "implTaskFoo");
  }

  private void doChangesTest(final String oldClassName, final String newClassName, final String changes) {
    final NameSuggester suggester = new NameSuggester(oldClassName, newClassName);
    assertEquals(
      changes,
      suggester.getChanges().toString()
    );
  }

  private void doSuggestionTest(final String oldClassName, final String newClassName, final String variableName,
                                final String expectedSuggestion) {
    final NameSuggester suggester = new NameSuggester(oldClassName, newClassName);
    assertEquals(expectedSuggestion, suggester.suggestName(variableName));
  }
}
