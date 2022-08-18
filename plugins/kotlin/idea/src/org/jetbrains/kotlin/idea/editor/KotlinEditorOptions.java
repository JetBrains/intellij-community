// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.editor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
        name = "JetEditorOptions",
        storages = {
                @Storage(
                        value = "$APP_CONFIG$/editor.xml"
                )},
        category = SettingsCategory.CODE
)
public class KotlinEditorOptions implements PersistentStateComponent<KotlinEditorOptions> {
    private boolean donTShowConversionDialog = false;
    private boolean enableJavaToKotlinConversion = true;
    private boolean autoAddValKeywordToDataClassParameters = true;

    public boolean isDonTShowConversionDialog() {
        return donTShowConversionDialog;
    }

    public void setDonTShowConversionDialog(boolean donTShowConversionDialog) {
        this.donTShowConversionDialog = donTShowConversionDialog;
    }

    public boolean isAutoAddValKeywordToDataClassParameters() {
        return autoAddValKeywordToDataClassParameters;
    }

    public void setAutoAddValKeywordToDataClassParameters(boolean autoAddValKeywordToDataClassParameters) {
        this.autoAddValKeywordToDataClassParameters = autoAddValKeywordToDataClassParameters;
    }

    public boolean isEnableJavaToKotlinConversion() {
        return enableJavaToKotlinConversion;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setEnableJavaToKotlinConversion(boolean enableJavaToKotlinConversion) {
        this.enableJavaToKotlinConversion = enableJavaToKotlinConversion;
    }

    @Override
    public KotlinEditorOptions getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull KotlinEditorOptions state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public static KotlinEditorOptions getInstance() {
        return ApplicationManager.getApplication().getService(KotlinEditorOptions.class);
    }

    @Override
    @Nullable
    public Object clone() {
        try {
            return super.clone();
        }
        catch (CloneNotSupportedException e) {
            return null;
        }
    }
}
