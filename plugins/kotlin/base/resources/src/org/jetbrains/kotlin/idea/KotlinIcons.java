// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea;

import com.intellij.ui.IconManager;
import icons.KotlinBaseResourcesIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public interface KotlinIcons {
    /** 16x16 */ @NotNull Icon SMALL_LOGO = KotlinBaseResourcesIcons.Kotlin;
    /** 13x13 */ @NotNull Icon SMALL_LOGO_13 = KotlinBaseResourcesIcons.Kotlin13;
    /** 16x16 */ @NotNull Icon ABSTRACT_EXTENSION_FUNCTION = KotlinBaseResourcesIcons.Abstract_extension_function;
    /** 16x16 */ @NotNull Icon ABSTRACT_CLASS = KotlinBaseResourcesIcons.AbstractClassKotlin;
    /** 16x16 */ @NotNull Icon ACTUAL = KotlinBaseResourcesIcons.Actual;
    /** 16x16 */ @NotNull Icon ANNOTATION = KotlinBaseResourcesIcons.AnnotationKotlin;
    /** 16x16 */ @NotNull Icon CLASS_INITIALIZER = KotlinBaseResourcesIcons.ClassInitializerKotlin;
    /** 16x16 */ @NotNull Icon CLASS = KotlinBaseResourcesIcons.ClassKotlin;
    /** 12x12 */ @NotNull Icon DSL_MARKER_ANNOTATION = KotlinBaseResourcesIcons.DslMarkerAnnotation;
    /** 16x16 */ @NotNull Icon ENUM = KotlinBaseResourcesIcons.EnumKotlin;
    /** 16x16 */ @NotNull Icon EXPECT = KotlinBaseResourcesIcons.Expect;
    Icon EXTENSION_FUNCTION = IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Function);
    /** 16x16 */ @NotNull Icon FIELD_VAL = KotlinBaseResourcesIcons.Field_value;
    /** 16x16 */ @NotNull Icon FIELD_VAR = KotlinBaseResourcesIcons.Field_variable;
    /** 16x16 */ @NotNull Icon FIR = KotlinBaseResourcesIcons.Fir;
    Icon FUNCTION = IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Function);
    /** 16x16 */ @NotNull Icon INTERFACE = KotlinBaseResourcesIcons.InterfaceKotlin;
    /** 16x16 */ @NotNull Icon FILE = KotlinBaseResourcesIcons.Kotlin_file;
    /** 16x16 */ @NotNull Icon GRADLE_SCRIPT = KotlinBaseResourcesIcons.Kotlin_gradle_script;
    /** 16x16 */ @NotNull Icon JS = KotlinBaseResourcesIcons.Kotlin_js;
    /** 16x16 */ @NotNull Icon LAUNCH = KotlinBaseResourcesIcons.Kotlin_launch_configuration;
    /** 16x16 */ @NotNull Icon MPP = KotlinBaseResourcesIcons.Kotlin_multiplatform_project;
    /** 16x16 */ @NotNull Icon NATIVE = KotlinBaseResourcesIcons.Kotlin_native;
    /** 16x16 */ @NotNull Icon SCRIPT = KotlinBaseResourcesIcons.Kotlin_script;
    /** 16x16 */ @NotNull Icon LAMBDA = KotlinBaseResourcesIcons.Lambda;
    /** 16x16 */ @NotNull Icon LOAD_SCRIPT_CONFIGURATION = KotlinBaseResourcesIcons.LoadScriptConfiguration;
    /** 16x16 */ @NotNull Icon OBJECT = KotlinBaseResourcesIcons.ObjectKotlin;
    Icon PARAMETER = IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Parameter);
    /** 12x12 */ @NotNull Icon SUSPEND_CALL = KotlinBaseResourcesIcons.SuspendCall;
    /** 16x16 */ @NotNull Icon TYPE_ALIAS = KotlinBaseResourcesIcons.TypeAlias;
    Icon VAR = IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Variable);
    /** 16x16 */ @NotNull Icon VAL = KotlinBaseResourcesIcons.Value;

    final class Wizard {
        /** 16x16 */ public static final @NotNull Icon ANDROID = KotlinBaseResourcesIcons.Wizard.Android;
        /** 16x16 */ public static final @NotNull Icon COMPOSE = KotlinBaseResourcesIcons.Wizard.Compose;
        /** 16x16 */ public static final @NotNull Icon CONSOLE = KotlinBaseResourcesIcons.Wizard.Console;
        /** 16x16 */ public static final @NotNull Icon IOS = KotlinBaseResourcesIcons.Wizard.Ios;
        /** 16x16 */ public static final @NotNull Icon JS = KotlinBaseResourcesIcons.Wizard.JS;
        /** 16x16 */ public static final @NotNull Icon JVM = KotlinBaseResourcesIcons.Wizard.Jvm;
        /** 16x16 */ public static final @NotNull Icon LINUX = KotlinBaseResourcesIcons.Wizard.Linux;
        /** 16x16 */ public static final @NotNull Icon MAC_OS = KotlinBaseResourcesIcons.Wizard.MacOS;
        /** 16x16 */ public static final @NotNull Icon MULTIPLATFORM = KotlinBaseResourcesIcons.Wizard.Multiplatform;
        /** 16x16 */ public static final @NotNull Icon MULTIPLATFORM_LIBRARY = KotlinBaseResourcesIcons.Wizard.MultiplatformLibrary;
        /** 16x16 */ public static final @NotNull Icon MULTIPLATFORM_MOBILE = KotlinBaseResourcesIcons.Wizard.MultiplatformMobile;
        /** 16x16 */ public static final @NotNull Icon MULTIPLATFORM_MOBILE_LIBRARY = KotlinBaseResourcesIcons.Wizard.MultiplatformMobileLibrary;
        /** 16x16 */ public static final @NotNull Icon NATIVE = KotlinBaseResourcesIcons.Wizard.Native;
        /** 16x16 */ public static final @NotNull Icon NODE_JS = KotlinBaseResourcesIcons.Wizard.Nodejs;
        /** 16x16 */ public static final @NotNull Icon WEB = KotlinBaseResourcesIcons.Wizard.PpWeb;
        /** 16x16 */ public static final @NotNull Icon REACT_JS = KotlinBaseResourcesIcons.Wizard.React;
        /** 16x16 */ public static final @NotNull Icon WINDOWS = KotlinBaseResourcesIcons.Wizard.Windows;
    }
}
