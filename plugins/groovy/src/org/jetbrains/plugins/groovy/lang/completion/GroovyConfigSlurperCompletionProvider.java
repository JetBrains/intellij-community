/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.PairConsumer;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.configSlurper.ConfigSlurperSupport;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.*;

/**
 * @author Sergey Evdokimov
 */
class GroovyConfigSlurperCompletionProvider extends CompletionProvider<CompletionParameters> {

  private final boolean myAddPrefixes;

  GroovyConfigSlurperCompletionProvider(boolean addPrefixes) {
    myAddPrefixes = addPrefixes;
  }

  public static void register(CompletionContributor contributor) {
    PsiElementPattern.Capture<PsiElement> pattern = PlatformPatterns.psiElement().withParent(PlatformPatterns.psiElement(GrReferenceExpression.class));

    contributor.extend(CompletionType.BASIC, pattern, new GroovyConfigSlurperCompletionProvider(true));
    contributor.extend(CompletionType.SMART, pattern, new GroovyConfigSlurperCompletionProvider(false));
  }

  @Override
  protected void addCompletions(@NotNull CompletionParameters parameters,
                                ProcessingContext context,
                                @NotNull CompletionResultSet result) {
    PsiFile file = parameters.getOriginalFile();
    if (!(file instanceof GroovyFile)) return;

    GroovyFile groovyFile = (GroovyFile)file;

    if (!groovyFile.isScript()) return;

    GrReferenceExpression ref = (GrReferenceExpression)parameters.getPosition().getParent();
    if (ref == null) return;

    final Map<String, Boolean> variants = new HashMap<>();
    collectVariants((s, isFinal) -> variants.put(s, isFinal), ref, groovyFile);

    if (variants.isEmpty()) return;

    // Remove existing variants.
    PsiElement parent = ref.getParent();
    if (parent instanceof GrAssignmentExpression) {
      parent = parent.getParent();
    }
    if (parent == null) return;

    Set<String> processedPrefixes = new HashSet<>();
    Set<String> prefixesInMethodCall = new HashSet<>();

    for (PsiElement e = parent.getFirstChild(); e != null; e = e.getNextSibling()) {
      if (e instanceof GrAssignmentExpression) {
        PsiElement left = ((GrAssignmentExpression)e).getLValue();
        if (left instanceof GrReferenceExpression) {
          String s = refToString((GrReferenceExpression)left);
          if (s == null) continue;

          int dotIndex = s.indexOf('.');
          if (dotIndex > 0) {
            processedPrefixes.add(s.substring(0, dotIndex));
          }

          variants.remove(s);
        }
      }
      else if (e instanceof GrMethodCall) {
        GrMethodCall call = (GrMethodCall)e;
        if (isPropertyCall(call)) {
          String name = extractPropertyName(call);
          if (name == null) continue;

          processedPrefixes.add(name);
          prefixesInMethodCall.add(name);
          variants.remove(name);
        }
      }
    }

    // Process variants.
    for (Map.Entry<String, Boolean> entry : variants.entrySet()) {
      String variant = entry.getKey();

      int dotIndex = variant.indexOf('.');
      if (dotIndex > 0 && dotIndex < variant.length() - 1) {
        String p = variant.substring(0, dotIndex);
        if (prefixesInMethodCall.contains(p)) continue;

        if (myAddPrefixes && processedPrefixes.add(p)) {
          result.addElement(LookupElementBuilder.create(p));
        }
      }

      LookupElement lookupElement = LookupElementBuilder.create(variant);
      if (entry.getValue()) {
        lookupElement = TailTypeDecorator.withTail(lookupElement, TailType.EQ);
      }

      result.addElement(lookupElement);
    }
  }

  private static void collectVariants(@NotNull PairConsumer<String, Boolean> consumer, @NotNull GrReferenceExpression ref, @NotNull GroovyFile originalFile) {
    List<String> prefix = getPrefix(ref);
    if (prefix == null) return;

    for (ConfigSlurperSupport configSlurperSupport : ConfigSlurperSupport.EP_NAME.getExtensions()) {
      ConfigSlurperSupport.PropertiesProvider provider = configSlurperSupport.getProvider(originalFile);
      if (provider == null) continue;

      provider.collectVariants(prefix, consumer);
    }
  }

  @Nullable
  private static String refToString(GrReferenceExpression ref) {
    StringBuilder sb = new StringBuilder();

    while (ref != null) {
      String name = ref.getReferenceName();
      if (name == null) return null;

      for (int i = name.length(); --i >= 0; ) {
        sb.append(name.charAt(i));
      }

      GrExpression qualifierExpression = ref.getQualifierExpression();
      if (qualifierExpression == null) break;

      if (!(qualifierExpression instanceof GrReferenceExpression)) return null;

      sb.append('.');

      ref = (GrReferenceExpression)qualifierExpression;
    }

    sb.reverse();

    return sb.toString();
  }

  @Nullable
  public static List<String> getPrefix(GrReferenceExpression ref) {
    List<String> res = new ArrayList<>();

    GrExpression qualifier = ref.getQualifierExpression();

    while (qualifier != null) {
      if (!(qualifier instanceof GrReferenceExpression)) return null;

      GrReferenceExpression r = (GrReferenceExpression)qualifier;

      String name = r.getReferenceName();
      if (name == null) return null;

      res.add(name);

      qualifier = r.getQualifierExpression();
    }

    PsiElement e = ref.getParent();

    if (e instanceof GrAssignmentExpression) {
      GrAssignmentExpression assignmentExpression = (GrAssignmentExpression)e;
      if (assignmentExpression.getLValue() != ref) return null;
      e = assignmentExpression.getParent();
    }

    while (true) {
      if (e instanceof PsiFile) {
        break;
      }
      else if (e instanceof GrClosableBlock) {
        PsiElement eCall = e.getParent();
        if (!(eCall instanceof GrMethodCall)) return null;

        GrMethodCall call = (GrMethodCall)eCall;

        if (!isPropertyCall(call)) return null;

        String name = extractPropertyName(call);
        if (name == null) return null;
        res.add(name);

        e = call.getParent();
      }
      else if (e instanceof GrBlockStatement || e instanceof GrOpenBlock || e instanceof GrIfStatement || e instanceof GrForStatement
          || e instanceof GrWhileStatement || e instanceof GrTryCatchStatement) {
        e = e.getParent();
      }
      else {
        return null;
      }
    }

    Collections.reverse(res);

    return res;
  }

  @Nullable
  private static String extractPropertyName(GrMethodCall call) {
    GrExpression ie = call.getInvokedExpression();

    if (ie instanceof GrReferenceExpression) {
      GrReferenceExpression r = (GrReferenceExpression)ie;
      if (r.isQualified()) return null;

      return r.getReferenceName();
    }

    if (ie instanceof GrLiteralImpl) {
      Object value = ((GrLiteralImpl)ie).getValue();
      if (!(value instanceof String)) return null;

      return (String)value;
    }

    return null;
  }

  private static boolean isPropertyCall(GrMethodCall call) {
    GrExpression[] arguments = PsiUtil.getAllArguments(call);
    return arguments.length == 1 && arguments[0] instanceof GrClosableBlock;
  }

}
