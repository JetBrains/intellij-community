// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea;

import com.intellij.icons.AllIcons;
import com.intellij.ui.IconManager;
import com.intellij.util.ImageLoader;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public interface KotlinIcons {
    private static @NotNull Icon load(@NotNull String path, long cacheKey, int flags) {
        return IconManager.getInstance().loadRasterizedIcon(path, KotlinIcons.class.getClassLoader(), cacheKey, flags);
    }

    private static @NotNull Icon load(@NotNull String path, long cacheKey) {
        return load(path, cacheKey, ImageLoader.USE_CACHE);
    }

    /** 16x16 */ @NotNull Icon SMALL_LOGO = load("org/jetbrains/kotlin/idea/icons/kotlin.svg", -6159597857264791813L);
    /** 13x13 */ @NotNull Icon SMALL_LOGO_13 = load("org/jetbrains/kotlin/idea/icons/kotlin13.svg", -5550034022007323579L);
    /** 16x16 */ @NotNull Icon ABSTRACT_EXTENSION_FUNCTION = load("org/jetbrains/kotlin/idea/icons/abstract_extension_function.svg", 7417679411263450358L);
    /** 16x16 */ @NotNull Icon ABSTRACT_CLASS = load("org/jetbrains/kotlin/idea/icons/abstractClassKotlin.svg", 6118422021574552028L);
    /** 16x16 */ @NotNull Icon ACTUAL = load("org/jetbrains/kotlin/idea/icons/actual.svg", -4727863439746678606L);
    /** 16x16 */ @NotNull Icon ANNOTATION = load("org/jetbrains/kotlin/idea/icons/annotationKotlin.svg", 1981410558175520747L);
    /** 16x16 */ @NotNull Icon CLASS_INITIALIZER = load("org/jetbrains/kotlin/idea/icons/classInitializerKotlin.svg", 428407677003035493L);
    /** 16x16 */ @NotNull Icon CLASS = load("org/jetbrains/kotlin/idea/icons/classKotlin.svg", -4723898843334724017L);
    /** 12x12 */ @NotNull Icon DSL_MARKER_ANNOTATION = load("org/jetbrains/kotlin/idea/icons/dslMarkerAnnotation.svg", 5574692736776531477L);
    /** 16x16 */ @NotNull Icon ENUM = load("org/jetbrains/kotlin/idea/icons/enumKotlin.svg", -8462447318313336813L);
    /** 16x16 */ @NotNull Icon EXPECT = load("org/jetbrains/kotlin/idea/icons/expect.svg", -2910408808136595896L, ImageLoader.USE_CACHE | ImageLoader.ALLOW_FLOAT_SCALING);
    Icon EXTENSION_FUNCTION = PlatformIcons.FUNCTION_ICON;
    /** 16x16 */ @NotNull Icon FIELD_VAL = load("org/jetbrains/kotlin/idea/icons/field_value.svg", 173519203061099646L);
    /** 16x16 */ @NotNull Icon FIELD_VAR = load("org/jetbrains/kotlin/idea/icons/field_variable.svg", -4943884320640938494L);
    Icon FUNCTION = AllIcons.Nodes.Function;
    /** 16x16 */ @NotNull Icon INTERFACE = load("org/jetbrains/kotlin/idea/icons/interfaceKotlin.svg", 6105411896669082060L, ImageLoader.USE_CACHE | ImageLoader.ALLOW_FLOAT_SCALING);
    /** 16x16 */ @NotNull Icon FILE = load("org/jetbrains/kotlin/idea/icons/kotlin_file.svg", 410462991849735236L);
    /** 16x16 */ @NotNull Icon GRADLE_SCRIPT = load("org/jetbrains/kotlin/idea/icons/kotlin_gradle_script.svg", 407924732847883692L);
    /** 16x16 */ @NotNull Icon JS = load("org/jetbrains/kotlin/idea/icons/kotlin_js.svg", -4797950250597216272L);
    /** 16x16 */ @NotNull Icon LAUNCH = load("org/jetbrains/kotlin/idea/icons/kotlin_launch_configuration.svg", 8617317266006756955L);
    /** 16x16 */ @NotNull Icon MPP = load("org/jetbrains/kotlin/idea/icons/kotlin_multiplatform_project.svg", 1800183257649593188L);
    /** 16x16 */ @NotNull Icon NATIVE = load("org/jetbrains/kotlin/idea/icons/kotlin_native.svg", 4139485729290770650L);
    /** 16x16 */ @NotNull Icon SCRIPT = load("org/jetbrains/kotlin/idea/icons/kotlin_script.svg", 8910176401343092076L);
    /** 16x16 */ @NotNull Icon LAMBDA = load("org/jetbrains/kotlin/idea/icons/lambda.svg", -395028811269734745L);
    /** 16x16 */ @NotNull Icon LOAD_SCRIPT_CONFIGURATION = load("org/jetbrains/kotlin/idea/icons/loadScriptConfiguration.svg", 4218830826949357848L);
    /** 16x16 */ @NotNull Icon OBJECT = load("org/jetbrains/kotlin/idea/icons/objectKotlin.svg", 6837434796032324128L);
    Icon PARAMETER = PlatformIcons.PARAMETER_ICON;
    /** 12x12 */ @NotNull Icon SUSPEND_CALL = load("org/jetbrains/kotlin/idea/icons/suspendCall.svg", -6406590614097998571L);
    /** 16x16 */ @NotNull Icon TYPE_ALIAS = load("org/jetbrains/kotlin/idea/icons/typeAlias.svg", -272854517691002937L);
    Icon VAR = PlatformIcons.VARIABLE_ICON;
    /** 16x16 */ @NotNull Icon VAL = load("org/jetbrains/kotlin/idea/icons/value.svg", 5883345383289852521L);

    final class Wizard {
        /** 16x16 */ public static final @NotNull Icon ANDROID = load("org/jetbrains/kotlin/idea/icons/wizard/android.svg", 7539975312323470635L);
        /** 16x16 */ public static final @NotNull Icon COMPOSE = load("org/jetbrains/kotlin/idea/icons/wizard/compose.svg", 160562836348075998L);
        /** 16x16 */ public static final @NotNull Icon CONSOLE = load("org/jetbrains/kotlin/idea/icons/wizard/console.svg", -8378080712919798557L);
        /** 16x16 */ public static final @NotNull Icon IOS = load("org/jetbrains/kotlin/idea/icons/wizard/ios.svg", -6207668788895415586L);
        /** 16x16 */ public static final @NotNull Icon JS = load("org/jetbrains/kotlin/idea/icons/wizard/js.svg", -688693395373848289L);
        /** 16x16 */ public static final @NotNull Icon JVM = load("org/jetbrains/kotlin/idea/icons/wizard/jvm.svg", 162781121887292258L);
        /** 16x16 */ public static final @NotNull Icon LINUX = load("org/jetbrains/kotlin/idea/icons/wizard/linux.svg", 3866831033229145983L);
        /** 16x16 */ public static final @NotNull Icon MAC_OS = load("org/jetbrains/kotlin/idea/icons/wizard/macOS.svg", -3403600566714905943L);
        /** 16x16 */ public static final @NotNull Icon MULTIPLATFORM = load("org/jetbrains/kotlin/idea/icons/wizard/multiplatform.svg", -6469017586665949204L);
        /** 16x16 */ public static final @NotNull Icon MULTIPLATFORM_LIBRARY = load("org/jetbrains/kotlin/idea/icons/wizard/multiplatformLibrary.svg", -5968042525882549362L);
        /** 16x16 */ public static final @NotNull Icon MULTIPLATFORM_MOBILE = load("org/jetbrains/kotlin/idea/icons/wizard/multiplatformMobile.svg", -8813901519156236976L);
        /** 16x16 */ public static final @NotNull Icon MULTIPLATFORM_MOBILE_LIBRARY = load("org/jetbrains/kotlin/idea/icons/wizard/multiplatformMobileLibrary.svg", -8434348402253626654L);
        /** 16x16 */ public static final @NotNull Icon NATIVE = load("org/jetbrains/kotlin/idea/icons/wizard/native.svg", 6669135310113032649L);
        /** 16x16 */ public static final @NotNull Icon NODE_JS = load("org/jetbrains/kotlin/idea/icons/wizard/nodejs.svg", -4106089222155620880L);
        /** 16x16 */ public static final @NotNull Icon WEB = load("org/jetbrains/kotlin/idea/icons/wizard/ppWeb.svg", -919003641439396121L);
        /** 16x16 */ public static final @NotNull Icon REACT_JS = load("org/jetbrains/kotlin/idea/icons/wizard/react.svg", -6765773272068717159L);
        /** 16x16 */ public static final @NotNull Icon WINDOWS = load("org/jetbrains/kotlin/idea/icons/wizard/windows.svg", 8510608514465228229L);
    }




}
