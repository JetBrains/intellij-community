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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.resolve.ast.AstTransformContributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.psi.util.PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT;
import static org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil.addExpandingReflectedMethods;
import static org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil.collectMethodsFromBody;

/**
 * Created by Max Medvedev on 03/03/14
 */
public class GrTypeDefinitionMembersCache {
  private static final Condition<PsiMethod> CONSTRUCTOR_CONDITION = new Condition<PsiMethod>() {
    @Override
    public boolean value(PsiMethod method) {
      return method.isConstructor();
    }
  };

  private final SimpleModificationTracker myTreeChangeTracker = new SimpleModificationTracker();

  private final GrTypeDefinition myDefinition;

  public GrTypeDefinitionMembersCache(GrTypeDefinition definition) {
    myDefinition = definition;
  }


  public GrMethod[] getCodeMethods() {
    return CachedValuesManager.getCachedValue(myDefinition, new CachedValueProvider<GrMethod[]>() {
      @Nullable
      @Override
      public Result<GrMethod[]> compute() {
        GrTypeDefinitionBody body = myDefinition.getBody();
        GrMethod[] methods = body != null ? body.getMethods() : GrMethod.EMPTY_ARRAY;
        return Result.create(methods, myTreeChangeTracker);
      }
    });
  }

  public GrMethod[] getCodeConstructors() {
    return CachedValuesManager.getCachedValue(myDefinition, new CachedValueProvider<GrMethod[]>() {
      @Nullable
      @Override
      public Result<GrMethod[]> compute() {
        GrTypeDefinitionBody body = myDefinition.getBody();
        GrMethod[] methods;
        if (body != null) {
          List<GrMethod> result = ContainerUtil.findAll(body.getMethods(), CONSTRUCTOR_CONDITION);
          methods = result.toArray(new GrMethod[result.size()]);
        }
        else {
          methods = GrMethod.EMPTY_ARRAY;
        }
        return Result.create(methods, myTreeChangeTracker);
      }
    });
  }

  public PsiMethod[] getConstructors() {
    return CachedValuesManager.getCachedValue(myDefinition, new CachedValueProvider<PsiMethod[]>() {
      @Nullable
      @Override
      public Result<PsiMethod[]> compute() {
        List<PsiMethod> result = ContainerUtil.findAll(myDefinition.getMethods(), CONSTRUCTOR_CONDITION);
        return Result.create(result.toArray(new PsiMethod[result.size()]), myTreeChangeTracker, OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
      }
    });
  }


  public PsiClass[] getInnerClasses() {
    return CachedValuesManager.getCachedValue(myDefinition, new CachedValueProvider<PsiClass[]>() {
      @Nullable
      @Override
      public Result<PsiClass[]> compute() {
        final GrTypeDefinitionBody body = myDefinition.getBody();
        PsiClass[] inners = body != null ? body.getInnerClasses() : PsiClass.EMPTY_ARRAY;
        return Result.create(inners, myTreeChangeTracker);
      }
    });
  }

  public GrField[] getFields() {
    return CachedValuesManager.getCachedValue(myDefinition, new CachedValueProvider<GrField[]>() {
      @Nullable
      @Override
      public Result<GrField[]> compute() {
        return Result.create(getFieldsImpl(), myTreeChangeTracker, OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
      }
    });
  }

  private GrField[] getFieldsImpl() {
    GrField[] codeFields = myDefinition.getCodeFields();

    List<GrField> fromAstTransform = getSyntheticFields();
    if (fromAstTransform.isEmpty()) return codeFields;

    GrField[] res = new GrField[codeFields.length + fromAstTransform.size()];
    System.arraycopy(codeFields, 0, res, 0, codeFields.length);

    for (int i = 0; i < fromAstTransform.size(); i++) {
      res[codeFields.length + i] = fromAstTransform.get(i);
    }

    return res;
  }

  private List<GrField> getSyntheticFields() {
    return CachedValuesManager.getCachedValue(myDefinition, new CachedValueProvider<List<GrField>>() {
      @Nullable
      @Override
      public Result<List<GrField>> compute() {
        return Result.create(AstTransformContributor.runContributorsForFields(myDefinition), myTreeChangeTracker, OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
      }
    });
  }

  public PsiMethod[] getMethods() {
    return CachedValuesManager.getCachedValue(myDefinition, new CachedValueProvider<PsiMethod[]>() {
      @Override
      public Result<PsiMethod[]> compute() {
        List<PsiMethod> result = new ArrayList<PsiMethod>();
        collectMethodsFromBody(myDefinition, result);

        for (PsiMethod method : AstTransformContributor.runContributorsForMethods(myDefinition)) {
          addExpandingReflectedMethods(result, method);
        }

        for (GrField field : getSyntheticFields()) {
          ContainerUtil.addIfNotNull(result, field.getSetter());
          Collections.addAll(result, field.getGetters());
        }
        return Result.create(result.toArray(new PsiMethod[result.size()]), myTreeChangeTracker, OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
      }
    });
  }

  public void dropCaches() {
    myTreeChangeTracker.incModificationCount();
  }
}
