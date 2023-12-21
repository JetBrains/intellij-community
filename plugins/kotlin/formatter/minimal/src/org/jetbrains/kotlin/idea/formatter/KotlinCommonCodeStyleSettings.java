// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.formatter;

import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleProvider;
import com.intellij.psi.codeStyle.arrangement.ArrangementSettings;
import com.intellij.psi.codeStyle.arrangement.ArrangementUtil;
import com.intellij.util.xmlb.XmlSerializer;
import kotlin.collections.ArraysKt;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.KotlinLanguage;
import org.jetbrains.kotlin.idea.util.ReflectionUtil;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

public class KotlinCommonCodeStyleSettings extends CommonCodeStyleSettings {
    @ReflectionUtil.SkipInEquals
    public String CODE_STYLE_DEFAULTS = null;

    private final boolean isTempForDeserialize;

    public KotlinCommonCodeStyleSettings() {
        this(false);
    }

    private KotlinCommonCodeStyleSettings(boolean isTempForDeserialize) {
        super(KotlinLanguage.INSTANCE);
        this.isTempForDeserialize = isTempForDeserialize;
    }

    private static KotlinCommonCodeStyleSettings createForTempDeserialize() {
        return new KotlinCommonCodeStyleSettings(true);
    }

    @Override
    public void readExternal(Element element) {
        if (isTempForDeserialize) {
            super.readExternal(element);
            return;
        }

        KotlinCommonCodeStyleSettings tempDeserialize = createForTempDeserialize();
        tempDeserialize.readExternal(element);

        applyKotlinCodeStyle(tempDeserialize.CODE_STYLE_DEFAULTS, this, true);

        super.readExternal(element);
    }

    @Override
    public void writeExternal(@NotNull Element element, @NotNull LanguageCodeStyleProvider provider) {
        CommonCodeStyleSettings defaultSettings = provider.getDefaultCommonSettings();
        applyKotlinCodeStyle(CODE_STYLE_DEFAULTS, defaultSettings, false);

        writeExternalBase(element, defaultSettings, provider);
    }

    //<editor-fold desc="Copied and adapted from CommonCodeStyleSettings ">
    private void writeExternalBase(
            @NotNull Element element,
            @NotNull CommonCodeStyleSettings defaultSettings,
            @NotNull LanguageCodeStyleProvider provider
    ) {
        Set<String> supportedFields = provider.getSupportedFields();
        if (supportedFields != null) {
            supportedFields.add("FORCE_REARRANGE_MODE");
            supportedFields.add("CODE_STYLE_DEFAULTS");
        } else {
            return;
        }

        //noinspection deprecation
        DefaultJDOMExternalizer.write(this, element, new SupportedFieldsDiffFilter(this, supportedFields, defaultSettings));
        List<Integer> softMargins = getSoftMargins();
        serializeInto(softMargins, element);

        IndentOptions myIndentOptions = getIndentOptions();
        if (myIndentOptions != null) {
            IndentOptions defaultIndentOptions = defaultSettings.getIndentOptions();
            Element indentOptionsElement = new Element(INDENT_OPTIONS_TAG);
            myIndentOptions.serialize(indentOptionsElement, defaultIndentOptions);
            if (!indentOptionsElement.getChildren().isEmpty()) {
                element.addContent(indentOptionsElement);
            }
        }

        ArrangementSettings myArrangementSettings = getArrangementSettings();
        if (myArrangementSettings != null) {
            Element container = new Element(ARRANGEMENT_ELEMENT_NAME);
            ArrangementUtil.writeExternal(container, myArrangementSettings, provider.getLanguage());
            if (!container.getChildren().isEmpty()) {
                element.addContent(container);
            }
        }
    }

    @Override
    public CommonCodeStyleSettings clone(@NotNull CodeStyleSettings rootSettings) {
        KotlinCommonCodeStyleSettings commonSettings = new KotlinCommonCodeStyleSettings();
        copyPublicFields(this, commonSettings);

        try {
            Method setRootSettingsMethod = CommonCodeStyleSettings.class.getDeclaredMethod("setRootSettings", CodeStyleSettings.class);
            setRootSettingsMethod.setAccessible(true);
            setRootSettingsMethod.invoke(commonSettings, rootSettings);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(e);
        }

        commonSettings.setForceArrangeMenuAvailable(isForceArrangeMenuAvailable());

        IndentOptions indentOptions = getIndentOptions();
        if (indentOptions != null) {
            IndentOptions targetIndentOptions = commonSettings.initIndentOptions();
            targetIndentOptions.copyFrom(indentOptions);
        }

        ArrangementSettings arrangementSettings = getArrangementSettings();
        if (arrangementSettings != null) {
            commonSettings.setArrangementSettings(arrangementSettings.clone());
        }

        try {
            Method setRootSettingsMethod = ArraysKt.singleOrNull(
                    CommonCodeStyleSettings.class.getDeclaredMethods(),
                    method -> "setSoftMargins".equals(method.getName()));

            if (setRootSettingsMethod != null) {
                // Method was introduced in 173
                setRootSettingsMethod.setAccessible(true);
                setRootSettingsMethod.invoke(commonSettings, getSoftMargins());
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(e);
        }

        return commonSettings;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof KotlinCommonCodeStyleSettings)) {
            return false;
        }

        if (!ReflectionUtil.comparePublicNonFinalFieldsWithSkip(this, obj)) {
            return false;
        }

        CommonCodeStyleSettings other = (CommonCodeStyleSettings) obj;
        if (!getSoftMargins().equals(other.getSoftMargins())) {
            return false;
        }

        IndentOptions options = getIndentOptions();
        if ((options == null && other.getIndentOptions() != null) ||
            (options != null && !options.equals(other.getIndentOptions()))) {
            return false;
        }

        return arrangementSettingsEqual(other);
    }

    // SoftMargins.serializeInfo
    private void serializeInto(@NotNull List<Integer> softMargins, @NotNull Element element) {
        if (softMargins.size() > 0) {
            XmlSerializer.serializeInto(this, element);
        }
    }
    //</editor-fold>

    //<editor-fold desc="Copied from CommonCodeStyleSettings">
    private static final String INDENT_OPTIONS_TAG = "indentOptions";
    private static final String ARRANGEMENT_ELEMENT_NAME = "arrangement";
    //</editor-fold>

    private static void applyKotlinCodeStyle(
            @Nullable String codeStyleId,
            @NotNull CommonCodeStyleSettings codeStyleSettings,
            Boolean modifyCodeStyle
    ) {
        if (codeStyleId != null) {
            switch (codeStyleId) {
                case KotlinOfficialStyleGuide.CODE_STYLE_ID: {
                    KotlinOfficialStyleGuide.applyToCommonSettings(codeStyleSettings, modifyCodeStyle);
                    break;
                }
                case  KotlinObsoleteStyleGuide.CODE_STYLE_ID: {
                    KotlinObsoleteStyleGuide.applyToCommonSettings(codeStyleSettings, modifyCodeStyle);
                    break;
                }
            }
        }
    }
}
