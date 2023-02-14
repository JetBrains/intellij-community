
package com.intellij.webSymbols.webTypes.json;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Specify rules for disabling web framework support. These rules take precedence over enable-when rules. They allow to turn off framework support in case of some conflicts between frameworks priority.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "file-extensions",
    "file-name-patterns"
})
public class DisablementRules {

    /**
     * Extensions of files, which should have the framework support disabled
     * 
     */
    @JsonProperty("file-extensions")
    @JsonPropertyDescription("Extensions of files, which should have the framework support disabled")
    private List<String> fileExtensions = new ArrayList<String>();
    /**
     * RegExp patterns to match file names, which should have the framework support disabled
     * 
     */
    @JsonProperty("file-name-patterns")
    @JsonPropertyDescription("RegExp patterns to match file names, which should have the framework support disabled")
    private List<Pattern> fileNamePatterns = new ArrayList<Pattern>();

    /**
     * Extensions of files, which should have the framework support disabled
     * 
     */
    @JsonProperty("file-extensions")
    public List<String> getFileExtensions() {
        return fileExtensions;
    }

    /**
     * Extensions of files, which should have the framework support disabled
     * 
     */
    @JsonProperty("file-extensions")
    public void setFileExtensions(List<String> fileExtensions) {
        this.fileExtensions = fileExtensions;
    }

    /**
     * RegExp patterns to match file names, which should have the framework support disabled
     * 
     */
    @JsonProperty("file-name-patterns")
    public List<Pattern> getFileNamePatterns() {
        return fileNamePatterns;
    }

    /**
     * RegExp patterns to match file names, which should have the framework support disabled
     * 
     */
    @JsonProperty("file-name-patterns")
    public void setFileNamePatterns(List<Pattern> fileNamePatterns) {
        this.fileNamePatterns = fileNamePatterns;
    }

}
