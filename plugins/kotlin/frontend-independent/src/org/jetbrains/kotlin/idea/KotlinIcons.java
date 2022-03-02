// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea;

import com.intellij.icons.AllIcons;
import com.intellij.util.PlatformIcons;
import icons.KotlinFirFrontendIndependentIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public interface KotlinIcons {
    /** 16x16 */ @NotNull Icon SMALL_LOGO = KotlinFirFrontendIndependentIcons.Kotlin;
    /** 13x13 */ @NotNull Icon SMALL_LOGO_13 = KotlinFirFrontendIndependentIcons.Kotlin13;
    /** 16x16 */ @NotNull Icon ABSTRACT_EXTENSION_FUNCTION = KotlinFirFrontendIndependentIcons.Abstract_extension_function;
    /** 16x16 */ @NotNull Icon ABSTRACT_CLASS = KotlinFirFrontendIndependentIcons.AbstractClassKotlin;
    /** 16x16 */ @NotNull Icon ACTUAL = KotlinFirFrontendIndependentIcons.Actual;
    /** 16x16 */ @NotNull Icon ANNOTATION = KotlinFirFrontendIndependentIcons.AnnotationKotlin;
    /** 16x16 */ @NotNull Icon CLASS_INITIALIZER = KotlinFirFrontendIndependentIcons.ClassInitializerKotlin;
    /** 16x16 */ @NotNull Icon CLASS = KotlinFirFrontendIndependentIcons.ClassKotlin;
    /** 12x12 */ @NotNull Icon DSL_MARKER_ANNOTATION = KotlinFirFrontendIndependentIcons.DslMarkerAnnotation;
    /** 16x16 */ @NotNull Icon ENUM = KotlinFirFrontendIndependentIcons.EnumKotlin;
    /** 16x16 */ @NotNull Icon EXPECT = KotlinFirFrontendIndependentIcons.Expect;
    Icon EXTENSION_FUNCTION = PlatformIcons.FUNCTION_ICON;
    /** 16x16 */ @NotNull Icon FIELD_VAL = KotlinFirFrontendIndependentIcons.Field_value;
    /** 16x16 */ @NotNull Icon FIELD_VAR = KotlinFirFrontendIndependentIcons.Field_variable;
    /** 16x16 */ @NotNull Icon FIR = KotlinFirFrontendIndependentIcons.Fir;
    Icon FUNCTION = AllIcons.Nodes.Function;
    /** 16x16 */ @NotNull Icon INTERFACE = KotlinFirFrontendIndependentIcons.InterfaceKotlin;
    /** 16x16 */ @NotNull Icon FILE = KotlinFirFrontendIndependentIcons.Kotlin_file;
    /** 16x16 */ @NotNull Icon GRADLE_SCRIPT = KotlinFirFrontendIndependentIcons.Kotlin_gradle_script;
    /** 16x16 */ @NotNull Icon JS = KotlinFirFrontendIndependentIcons.Kotlin_js;
    /** 16x16 */ @NotNull Icon LAUNCH = KotlinFirFrontendIndependentIcons.Kotlin_launch_configuration;
    /** 16x16 */ @NotNull Icon MPP = KotlinFirFrontendIndependentIcons.Kotlin_multiplatform_project;
    /** 16x16 */ @NotNull Icon NATIVE = KotlinFirFrontendIndependentIcons.Kotlin_native;
    /** 16x16 */ @NotNull Icon SCRIPT = KotlinFirFrontendIndependentIcons.Kotlin_script;
    /** 16x16 */ @NotNull Icon LAMBDA = KotlinFirFrontendIndependentIcons.Lambda;
    /** 16x16 */ @NotNull Icon LOAD_SCRIPT_CONFIGURATION = KotlinFirFrontendIndependentIcons.LoadScriptConfiguration;
    /** 16x16 */ @NotNull Icon OBJECT = KotlinFirFrontendIndependentIcons.ObjectKotlin;
    Icon PARAMETER = PlatformIcons.PARAMETER_ICON;
    /** 12x12 */ @NotNull Icon SUSPEND_CALL = KotlinFirFrontendIndependentIcons.SuspendCall;
    /** 16x16 */ @NotNull Icon TYPE_ALIAS = KotlinFirFrontendIndependentIcons.TypeAlias;
    Icon VAR = PlatformIcons.VARIABLE_ICON;
    /** 16x16 */ @NotNull Icon VAL = KotlinFirFrontendIndependentIcons.Value;

    final class Wizard {
        /** 16x16 */ public static final @NotNull Icon ANDROID = KotlinFirFrontendIndependentIcons.Wizard.Android;
        /** 16x16 */ public static final @NotNull Icon COMPOSE = KotlinFirFrontendIndependentIcons.Wizard.Compose;
        /** 16x16 */ public static final @NotNull Icon CONSOLE = KotlinFirFrontendIndependentIcons.Wizard.Console;
        /** 16x16 */ public static final @NotNull Icon IOS = KotlinFirFrontendIndependentIcons.Wizard.Ios;
        /** 16x16 */ public static final @NotNull Icon JS = KotlinFirFrontendIndependentIcons.Wizard.JS;
        /** 16x16 */ public static final @NotNull Icon JVM = KotlinFirFrontendIndependentIcons.Wizard.Jvm;
        /** 16x16 */ public static final @NotNull Icon LINUX = KotlinFirFrontendIndependentIcons.Wizard.Linux;
        /** 16x16 */ public static final @NotNull Icon MAC_OS = KotlinFirFrontendIndependentIcons.Wizard.MacOS;
        /** 16x16 */ public static final @NotNull Icon MULTIPLATFORM = KotlinFirFrontendIndependentIcons.Wizard.Multiplatform;
        /** 16x16 */ public static final @NotNull Icon MULTIPLATFORM_LIBRARY = KotlinFirFrontendIndependentIcons.Wizard.MultiplatformLibrary;
        /** 16x16 */ public static final @NotNull Icon MULTIPLATFORM_MOBILE = KotlinFirFrontendIndependentIcons.Wizard.MultiplatformMobile;
        /** 16x16 */ public static final @NotNull Icon MULTIPLATFORM_MOBILE_LIBRARY = KotlinFirFrontendIndependentIcons.Wizard.MultiplatformMobileLibrary;
        /** 16x16 */ public static final @NotNull Icon NATIVE = KotlinFirFrontendIndependentIcons.Wizard.Native;
        /** 16x16 */ public static final @NotNull Icon NODE_JS = KotlinFirFrontendIndependentIcons.Wizard.Nodejs;
        /** 16x16 */ public static final @NotNull Icon WEB = KotlinFirFrontendIndependentIcons.Wizard.PpWeb;
        /** 16x16 */ public static final @NotNull Icon REACT_JS = KotlinFirFrontendIndependentIcons.Wizard.React;
        /** 16x16 */ public static final @NotNull Icon WINDOWS = KotlinFirFrontendIndependentIcons.Wizard.Windows;
    }
}
