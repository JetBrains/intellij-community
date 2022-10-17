// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.lang.jvm.*;
import com.intellij.lang.jvm.types.JvmReferenceType;
import com.intellij.lang.jvm.types.JvmType;
import com.intellij.lang.jvm.util.JvmInheritanceUtil;
import com.intellij.lang.jvm.util.JvmUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThreadAware;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.JBTreeTraverser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;

public class MissingActionUpdateThread extends DevKitJvmInspection {

  @Nullable
  @Override
  protected JvmElementVisitor<Boolean> buildVisitor(@NotNull Project project, @NotNull HighlightSink sink, boolean isOnTheFly) {
    return new DefaultJvmElementVisitor<>() {
      @Override
      public Boolean visitClass(@NotNull JvmClass clazz) {
        if (clazz.getClassKind() != JvmClassKind.CLASS ||
            clazz.hasModifier(JvmModifier.ABSTRACT) ||
            !JvmInheritanceUtil.isInheritor(clazz, ActionUpdateThreadAware.class.getName())) {
          return false;
        }
        boolean isAnAction = false;
        boolean hasUpdateMethod = false;
        JBIterable<JvmReferenceType> superInterfaces = JBIterable.empty();
        for (JvmClass c = clazz; c != null; c = JvmUtil.resolveClass(c.getSuperClassType())) {
          String className = c.getQualifiedName();
          if (CommonClassNames.JAVA_LANG_OBJECT.equals(className) ||
              (isAnAction = AnAction.class.getName().equals(className))) {
            break;
          }
          for (JvmMethod method : c.getMethods()) {
            String name = method.getName();
            if ("getActionUpdateThread".equals(name) && method.getParameters().length == 0) {
              return null;
            }
            else if ("update".equals(name) && !hasUpdateMethod) {
              JvmParameter[] parameters = method.getParameters();
              JvmType pt = parameters.length == 1 ? parameters[0].getType() : null;
              JvmClass pc = pt instanceof JvmReferenceType ? JvmUtil.resolveClass((JvmReferenceType)pt) : null;
              if (pc != null && AnActionEvent.class.getName().equals(pc.getQualifiedName())) {
                hasUpdateMethod = true;
              }
            }
          }
          superInterfaces = superInterfaces.append(JBIterable.of(c.getInterfaceTypes()));
        }
        if (!isAnAction) {
          // Check super-interfaces for default methods - no need to check if the method is default.
          // Default override is good, non-default override without the implementation is a compiler error.
          JBTreeTraverser<JvmClass> traverser = JBTreeTraverser.from(o -> JBIterable.of(o.getSuperClassType())
            .filterMap(JvmUtil::resolveClass));
          for (JvmClass c : traverser.unique().withRoots(superInterfaces.filterMap(JvmUtil::resolveClass))) {
            if (ActionUpdateThreadAware.class.getName().equals(c.getQualifiedName())) continue;
            for (JvmMethod method : c.getMethods()) {
              if ("getActionUpdateThread".equals(method.getName()) && method.getParameters().length == 0) {
                return null;
              }
            }
          }
        }
        if (!isAnAction || hasUpdateMethod) {
          sink.highlight(DevKitBundle.message("inspections.action.update.thread.message"));
        }
        return false;
      }
    };
  }

}
