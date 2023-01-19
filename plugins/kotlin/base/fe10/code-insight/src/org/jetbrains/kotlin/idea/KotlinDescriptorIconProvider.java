// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.ui.IconManager;
import com.intellij.ui.RowIcon;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.descriptors.*;
import org.jetbrains.kotlin.descriptors.impl.LocalVariableDescriptor;
import org.jetbrains.kotlin.psi.KtElement;

import javax.swing.*;

public final class KotlinDescriptorIconProvider {
    private static final Logger LOG = Logger.getInstance("#org.jetbrains.kotlin.idea.KotlinDescriptorIconProvider");

    private KotlinDescriptorIconProvider() {
    }

    @Nullable
    public static Icon getIcon(@NotNull DeclarationDescriptor descriptor, @Nullable PsiElement declaration, @Iconable.IconFlags int flags) {
        if (declaration != null && !(declaration instanceof KtElement)) {
            return declaration.getIcon(flags);
        }

        Icon result = getBaseIcon(descriptor);
        if ((flags & Iconable.ICON_FLAG_VISIBILITY) > 0) {
            RowIcon rowIcon = new RowIcon(2);
            rowIcon.setIcon(result, 0);
            rowIcon.setIcon(getVisibilityIcon(descriptor), 1);
            result = rowIcon;
        }

        return result;
    }

    private static Icon getVisibilityIcon(@NotNull DeclarationDescriptor descriptor) {
        if (descriptor instanceof DeclarationDescriptorWithVisibility) {
            DeclarationDescriptorWithVisibility descriptorWithVisibility = (DeclarationDescriptorWithVisibility) descriptor;
            DescriptorVisibility visibility = descriptorWithVisibility.getVisibility().normalize();
            if (visibility == DescriptorVisibilities.PUBLIC) {
                return PlatformIcons.PUBLIC_ICON;
            }

            if (visibility == DescriptorVisibilities.PROTECTED) {
                return IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Protected);
            }

            if (DescriptorVisibilities.isPrivate(visibility)) {
                return IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Private);
            }

            if (visibility == DescriptorVisibilities.INTERNAL) {
                return IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Local);
            }
        }

        return null;
    }

    private static Modality getModalitySafe(@NotNull MemberDescriptor descriptor) {
        try {
            return descriptor.getModality();
        }
        catch (InvalidModuleException ex) {
            return Modality.FINAL;
        }
    }

    private static Icon getBaseIcon(@NotNull DeclarationDescriptor descriptor) {
        if (descriptor instanceof PackageFragmentDescriptor || descriptor instanceof PackageViewDescriptor) {
            return AllIcons.Nodes.Package;
        }
        if (descriptor instanceof FunctionDescriptor) {
            FunctionDescriptor functionDescriptor = (FunctionDescriptor) descriptor;
            if (functionDescriptor.getExtensionReceiverParameter() != null) {
                return Modality.ABSTRACT == getModalitySafe(functionDescriptor)
                       ? KotlinIcons.ABSTRACT_EXTENSION_FUNCTION
                       : KotlinIcons.EXTENSION_FUNCTION;
            }

            if (descriptor.getContainingDeclaration() instanceof ClassDescriptor) {
                return Modality.ABSTRACT == getModalitySafe(functionDescriptor)
                       ? PlatformIcons.ABSTRACT_METHOD_ICON
                       : IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Method);
            }
            else {
                return KotlinIcons.FUNCTION;
            }
        }
        if (descriptor instanceof ClassDescriptor) {
            ClassDescriptor classDescriptor = (ClassDescriptor) descriptor;
            return switch (classDescriptor.getKind()) {
                case INTERFACE -> KotlinIcons.INTERFACE;
                case ENUM_CLASS, ENUM_ENTRY -> KotlinIcons.ENUM;
                case ANNOTATION_CLASS -> KotlinIcons.ANNOTATION;
                case OBJECT -> KotlinIcons.OBJECT;
                case CLASS -> Modality.ABSTRACT == getModalitySafe(classDescriptor) ?
                              KotlinIcons.ABSTRACT_CLASS :
                              KotlinIcons.CLASS;
            };
        }
        if (descriptor instanceof ValueParameterDescriptor) {
            return KotlinIcons.PARAMETER;
        }

        if (descriptor instanceof LocalVariableDescriptor) {
            return ((VariableDescriptor) descriptor).isVar() ? KotlinIcons.VAR : KotlinIcons.VAL;
        }

        if (descriptor instanceof PropertyDescriptor) {
            return ((VariableDescriptor) descriptor).isVar() ? KotlinIcons.FIELD_VAR : KotlinIcons.FIELD_VAL;
        }

        if (descriptor instanceof TypeParameterDescriptor) {
            return IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Class);
        }

        if (descriptor instanceof TypeAliasDescriptor) {
            return KotlinIcons.TYPE_ALIAS;
        }

        LOG.warn("No icon for descriptor: " + descriptor);
        return null;
    }
}
