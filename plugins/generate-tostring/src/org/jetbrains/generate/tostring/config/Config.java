/*
 * Copyright 2001-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.generate.tostring.config;

import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jetbrains.generate.tostring.template.TemplateResource;
import org.jetbrains.generate.tostring.template.TemplateResourceLocator;
import org.jdom.Element;

import java.io.Serializable;

/**
 * Configuration.
 * <p/>
 * The configuration is stored using {@link JDOMExternalizable} that automatically stores the
 * state of this classes public fields.
 */
public class Config implements JDOMExternalizable, Serializable {

    public boolean useFullyQualifiedName = false;
    public boolean useFieldChooserDialog = true;
    public boolean useDefaultAlways = false;
    public ConflictResolutionPolicy replaceDialogInitialOption = PolicyOptions.getConflictOptions()[0];
    public InsertNewMethodPolicy insertNewMethodInitialOption = PolicyOptions.getNewMethodOptions()[0];
    public String methodBody = null;
    public boolean filterConstantField = true;
    public boolean filterEnumField = false;
    public boolean filterTransientModifier = false;
    public boolean filterStaticModifier = true;
    public String filterFieldName = null;
    public String filterMethodName = null;
    public String filterMethodType = null;
    public String filterFieldType = null;
    public boolean filterLoggers = true;
    public boolean addImplementSerializable = false;
    public boolean autoImports = false;
    public String autoImportsPackages = "java.util.*,java.text.*";
    public boolean inspectionOnTheFly = false;
    public boolean enableMethods = false;
    public boolean enableTemplateQuickList = false;
    public String selectedQuickTemplates = null;
    public boolean jumpToMethod = true; // jump cursor to toString method
    public int sortElements = 0; // 0 = none, 1 = asc, 2 = desc

    public boolean isUseFullyQualifiedName() {
        return useFullyQualifiedName;
    }

    public void setUseFullyQualifiedName(boolean useFullyQualifiedName) {
        this.useFullyQualifiedName = useFullyQualifiedName;
    }

    public String getMethodBody() {
        return methodBody;
    }

    public void setMethodBody(String methodBody) {
        this.methodBody = methodBody;
    }

    public boolean isUseFieldChooserDialog() {
        return useFieldChooserDialog;
    }

    public void setUseFieldChooserDialog(boolean useFieldChooserDialog) {
        this.useFieldChooserDialog = useFieldChooserDialog;
    }

    public boolean isUseDefaultAlways() {
        return useDefaultAlways;
    }

    public void setUseDefaultAlways(boolean useDefaultAlways) {
        this.useDefaultAlways = useDefaultAlways;
    }

    public ConflictResolutionPolicy getReplaceDialogInitialOption() {
        return replaceDialogInitialOption;
    }

    public void setReplaceDialogInitialOption(ConflictResolutionPolicy replaceDialogInitialOption) {
        this.replaceDialogInitialOption = replaceDialogInitialOption;
    }

    public InsertNewMethodPolicy getInsertNewMethodInitialOption() {
        return this.insertNewMethodInitialOption;
    }

    public void setInsertNewMethodInitialOption(InsertNewMethodPolicy insertNewMethodInitialOption) {
        this.insertNewMethodInitialOption = insertNewMethodInitialOption;
    }

    public boolean isFilterConstantField() {
        return filterConstantField;
    }

    public void setFilterConstantField(boolean filterConstantField) {
        this.filterConstantField = filterConstantField;
    }

    public boolean isFilterTransientModifier() {
        return filterTransientModifier;
    }

    public void setFilterTransientModifier(boolean filterTransientModifier) {
        this.filterTransientModifier = filterTransientModifier;
    }

    public boolean isFilterStaticModifier() {
        return filterStaticModifier;
    }

    public void setFilterStaticModifier(boolean filterStaticModifier) {
        this.filterStaticModifier = filterStaticModifier;
    }

    public String getFilterFieldName() {
        return filterFieldName;
    }

    public void setFilterFieldName(String filterFieldName) {
        this.filterFieldName = filterFieldName;
    }

    public boolean isAddImplementSerializable() {
        return addImplementSerializable;
    }

    public void setAddImplementSerializable(boolean addImplementSerializable) {
        this.addImplementSerializable = addImplementSerializable;
    }

    public boolean isAutoImports() {
        return autoImports;
    }

    public void setAutoImports(boolean autoImports) {
        this.autoImports = autoImports;
    }

    public String getAutoImportsPackages() {
        return autoImportsPackages;
    }

    public void setAutoImportsPackages(String autoImportsPackages) {
        this.autoImportsPackages = autoImportsPackages;
    }

    public boolean isInspectionOnTheFly() {
        return inspectionOnTheFly;
    }

    public void setInspectionOnTheFly(boolean inspectionOnTheFly) {
        this.inspectionOnTheFly = inspectionOnTheFly;
    }

    public boolean isEnableMethods() {
        return enableMethods;
    }

    public void setEnableMethods(boolean enableMethods) {
        this.enableMethods = enableMethods;
    }

    public String getFilterMethodName() {
        return filterMethodName;
    }

    public void setFilterMethodName(String filterMethodName) {
        this.filterMethodName = filterMethodName;
    }

    public boolean isEnableTemplateQuickList() {
        return enableTemplateQuickList;
    }

    public void setEnableTemplateQuickList(boolean enableTemplateQuickList) {
        this.enableTemplateQuickList = enableTemplateQuickList;
    }

    public String getSelectedQuickTemplates() {
        return selectedQuickTemplates;
    }

    public void setSelectedQuickTemplates(String selectedQuickTemplates) {
        this.selectedQuickTemplates = selectedQuickTemplates;
    }

    public boolean isJumpToMethod() {
        return jumpToMethod;
    }

    public void setJumpToMethod(boolean jumpToMethod) {
        this.jumpToMethod = jumpToMethod;
    }

    public boolean isFilterEnumField() {
        return filterEnumField;
    }

    public void setFilterEnumField(boolean filterEnumField) {
        this.filterEnumField = filterEnumField;
    }

    public int getSortElements() {
        return sortElements;
    }

    public void setSortElements(int sortElements) {
        this.sortElements = sortElements;
    }

    public String getFilterFieldType() {
        return filterFieldType;
    }

    public void setFilterFieldType(String filterFieldType) {
        this.filterFieldType = filterFieldType;
    }

    public boolean isFilterLoggers() {
        return filterLoggers;
    }

    public void setFilterLoggers(boolean filterLoggers) {
        this.filterLoggers = filterLoggers;
    }

    public String getFilterMethodType() {
        return filterMethodType;
    }

    public void setFilterMethodType(String filterMethodType) {
        this.filterMethodType = filterMethodType;
    }

    public void readExternal(Element element) throws InvalidDataException {
        DefaultJDOMExternalizer.readExternal(this, element);
    }

    public void writeExternal(Element element) throws WriteExternalException {
        DefaultJDOMExternalizer.writeExternal(this, element);
    }

    public TemplateResource getActiveTemplate() {
        // if just installed plugin methodBody can be null so use the default template instead
        if (methodBody == null)
            methodBody = TemplateResourceLocator.getDefaultTemplateBody();
        return new TemplateResource("--> Active Template <--", methodBody);
    }

    /**
     * Get's the filter pattern that this configuration represent.
     *
     * @return the filter pattern.
     */
    public FilterPattern getFilterPattern() {
        FilterPattern pattern = new FilterPattern();
        pattern.setConstantField(filterConstantField);
        pattern.setTransientModifier(filterTransientModifier);
        pattern.setStaticModifier(filterStaticModifier);
        pattern.setFieldName(filterFieldName);
        pattern.setFieldType(filterFieldType);
        pattern.setMethodName(filterMethodName);
        pattern.setMethodType(filterMethodType);
        pattern.setEnumField(filterEnumField);
        pattern.setLoggers(filterLoggers);
        return pattern;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Config config = (Config) o;

        if (addImplementSerializable != config.addImplementSerializable) return false;
        if (autoImports != config.autoImports) return false;
        if (enableMethods != config.enableMethods) return false;
        if (enableTemplateQuickList != config.enableTemplateQuickList) return false;
        if (filterConstantField != config.filterConstantField) return false;
        if (filterEnumField != config.filterEnumField) return false;
        if (filterStaticModifier != config.filterStaticModifier) return false;
        if (filterTransientModifier != config.filterTransientModifier) return false;
        if (inspectionOnTheFly != config.inspectionOnTheFly) return false;
        if (jumpToMethod != config.jumpToMethod) return false;
        if (sortElements != config.sortElements) return false;
        if (useDefaultAlways != config.useDefaultAlways) return false;
        if (useFieldChooserDialog != config.useFieldChooserDialog) return false;
        if (useFullyQualifiedName != config.useFullyQualifiedName) return false;
        if (autoImportsPackages != null ? !autoImportsPackages.equals(config.autoImportsPackages) : config.autoImportsPackages != null)
            return false;
        if (filterFieldName != null ? !filterFieldName.equals(config.filterFieldName) : config.filterFieldName != null)
            return false;
        if (filterFieldType != null ? !filterFieldType.equals(config.filterFieldType) : config.filterFieldType != null)
            return false;
        if (filterMethodName != null ? !filterMethodName.equals(config.filterMethodName) : config.filterMethodName != null)
            return false;
        if (filterMethodType != null ? !filterMethodType.equals(config.filterMethodType) : config.filterMethodType != null)
            return false;
        if (!insertNewMethodInitialOption.equals(config.insertNewMethodInitialOption)) return false;
        if (methodBody != null ? !methodBody.equals(config.methodBody) : config.methodBody != null) return false;
        if (!replaceDialogInitialOption.equals(config.replaceDialogInitialOption)) return false;
        if (selectedQuickTemplates != null ? !selectedQuickTemplates.equals(config.selectedQuickTemplates) : config.selectedQuickTemplates != null)
            return false;

        return true;
    }

    public int hashCode() {
        int result;
        result = (useFullyQualifiedName ? 1 : 0);
        result = 29 * result + (useFieldChooserDialog ? 1 : 0);
        result = 29 * result + (useDefaultAlways ? 1 : 0);
        result = 29 * result + replaceDialogInitialOption.hashCode();
        result = 29 * result + insertNewMethodInitialOption.hashCode();
        result = 29 * result + (methodBody != null ? methodBody.hashCode() : 0);
        result = 29 * result + (filterConstantField ? 1 : 0);
        result = 29 * result + (filterEnumField ? 1 : 0);
        result = 29 * result + (filterTransientModifier ? 1 : 0);
        result = 29 * result + (filterStaticModifier ? 1 : 0);
        result = 29 * result + (filterFieldName != null ? filterFieldName.hashCode() : 0);
        result = 29 * result + (filterFieldType != null ? filterFieldType.hashCode() : 0);
        result = 29 * result + (filterMethodName != null ? filterMethodName.hashCode() : 0);
        result = 29 * result + (filterMethodType != null ? filterMethodType.hashCode() : 0);
        result = 29 * result + (addImplementSerializable ? 1 : 0);
        result = 29 * result + (autoImports ? 1 : 0);
        result = 29 * result + (autoImportsPackages != null ? autoImportsPackages.hashCode() : 0);
        result = 29 * result + (inspectionOnTheFly ? 1 : 0);
        result = 29 * result + (enableMethods ? 1 : 0);
        result = 29 * result + (enableTemplateQuickList ? 1 : 0);
        result = 29 * result + (selectedQuickTemplates != null ? selectedQuickTemplates.hashCode() : 0);
        result = 29 * result + (jumpToMethod ? 1 : 0);
        result = 29 * result + sortElements;
        return result;
    }

    public String toString() {
        return "Config{" +
                "useFullyQualifiedName=" + useFullyQualifiedName +
                ", useFieldChooserDialog=" + useFieldChooserDialog +
                ", useDefaultAlways=" + useDefaultAlways +
                ", replaceDialogInitialOption=" + replaceDialogInitialOption +
                ", insertNewMethodInitialOption=" + insertNewMethodInitialOption +
                ", methodBody='" + methodBody + "'" +
                ", filterConstantField=" + filterConstantField +
                ", filterEnumField=" + filterEnumField +
                ", filterTransientModifier=" + filterTransientModifier +
                ", filterStaticModifier=" + filterStaticModifier +
                ", filterFieldName='" + filterFieldName + "'" +
                ", filterFieldType='" + filterFieldType + "'" +
                ", filterMethodName='" + filterMethodName + "'" +
                ", filterMethodType='" + filterMethodType + "'" +
                ", addImplementSerializable=" + addImplementSerializable +
                ", autoImports=" + autoImports +
                ", autoImportsPackages='" + autoImportsPackages + "'" +
                ", inspectionOnTheFly=" + inspectionOnTheFly +
                ", enableMethods=" + enableMethods +
                ", enableTemplateQuickList=" + enableTemplateQuickList +
                ", selectedQuickTemplates='" + selectedQuickTemplates + "'" +
                ", jumpToMethod=" + jumpToMethod +
                ", sortElements=" + sortElements +
                "}";
    }

}