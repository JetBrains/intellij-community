// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.util;

import com.intellij.openapi.util.Comparing;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.function.Predicate;

public final class ReflectionUtil {
    @Retention(RetentionPolicy.RUNTIME)
    public @interface SkipInEquals {
    }

    public static boolean comparePublicNonFinalFieldsWithSkip(@NotNull Object first, @NotNull Object second) {
        return comparePublicNonFinalFields(first, second, field -> field.getAnnotation(SkipInEquals.class) == null);
    }

    private static boolean comparePublicNonFinalFields(
            @NotNull Object first,
            @NotNull Object second,
            @Nullable Predicate<Field> acceptPredicate
    ) {
        Set<Field> firstFields = ContainerUtil.newHashSet(first.getClass().getFields());

        for (Field field : second.getClass().getFields()) {
            if (firstFields.contains(field)) {
                if (isPublic(field) && !isFinal(field) && (acceptPredicate == null || acceptPredicate.test(field))) {
                    try {
                        if (!Comparing.equal(field.get(first), field.get(second))) {
                            return false;
                        }
                    }
                    catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        return true;
    }

    private static boolean isPublic(Field field) {
        return (field.getModifiers() & Modifier.PUBLIC) != 0;
    }

    private static boolean isFinal(Field field) {
        return (field.getModifiers() & Modifier.FINAL) != 0;
    }
}
