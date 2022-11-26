
package com.intellij.webSymbols.webTypes.json;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Specify rules for enabling web framework support. Only one framework can be enabled in a particular file. If you need your contributions to be enabled in all files, regardless of the context, do not specify the framework.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "node-packages",
    "script-url-patterns",
    "file-extensions",
    "file-name-patterns",
    "ide-libraries"
})
public class EnablementRules {

    /**
     * Node.js package names, which enable framework support within the folder containing the package.json.
     * 
     */
    @JsonProperty("node-packages")
    @JsonPropertyDescription("Node.js package names, which enable framework support within the folder containing the package.json.")
    private List<String> nodePackages = new ArrayList<String>();
    /**
     * RegExps to match script URLs, which enable framework support within a particular HTML.
     * 
     */
    @JsonProperty("script-url-patterns")
    @JsonPropertyDescription("RegExps to match script URLs, which enable framework support within a particular HTML.")
    private List<Pattern> scriptUrlPatterns = new ArrayList<Pattern>();
    /**
     * Extensions of files, which should have the framework support enabled. Use this to support custom file extensions like '.vue' or '.svelte'. Never specify generic extensions like '.html', '.js' or '.ts'. If you need your contributions to be present in every file don't specify the framework at all
     * 
     */
    @JsonProperty("file-extensions")
    @JsonPropertyDescription("Extensions of files, which should have the framework support enabled. Use this to support custom file extensions like '.vue' or '.svelte'. Never specify generic extensions like '.html', '.js' or '.ts'. If you need your contributions to be present in every file don't specify the framework at all")
    private List<String> fileExtensions = new ArrayList<String>();
    /**
     * RegExp patterns to match file names, which should have the framework support enabled. Use carefully as broken pattern may even freeze IDE.
     * 
     */
    @JsonProperty("file-name-patterns")
    @JsonPropertyDescription("RegExp patterns to match file names, which should have the framework support enabled. Use carefully as broken pattern may even freeze IDE.")
    private List<Pattern> fileNamePatterns = new ArrayList<Pattern>();
    /**
     * Global JavaScript libraries names enabled within the IDE, which enable framework support in the whole project
     * 
     */
    @JsonProperty("ide-libraries")
    @JsonPropertyDescription("Global JavaScript libraries names enabled within the IDE, which enable framework support in the whole project")
    private List<String> ideLibraries = new ArrayList<String>();

    /**
     * Node.js package names, which enable framework support within the folder containing the package.json.
     * 
     */
    @JsonProperty("node-packages")
    public List<String> getNodePackages() {
        return nodePackages;
    }

    /**
     * Node.js package names, which enable framework support within the folder containing the package.json.
     * 
     */
    @JsonProperty("node-packages")
    public void setNodePackages(List<String> nodePackages) {
        this.nodePackages = nodePackages;
    }

    /**
     * RegExps to match script URLs, which enable framework support within a particular HTML.
     * 
     */
    @JsonProperty("script-url-patterns")
    public List<Pattern> getScriptUrlPatterns() {
        return scriptUrlPatterns;
    }

    /**
     * RegExps to match script URLs, which enable framework support within a particular HTML.
     * 
     */
    @JsonProperty("script-url-patterns")
    public void setScriptUrlPatterns(List<Pattern> scriptUrlPatterns) {
        this.scriptUrlPatterns = scriptUrlPatterns;
    }

    /**
     * Extensions of files, which should have the framework support enabled. Use this to support custom file extensions like '.vue' or '.svelte'. Never specify generic extensions like '.html', '.js' or '.ts'. If you need your contributions to be present in every file don't specify the framework at all
     * 
     */
    @JsonProperty("file-extensions")
    public List<String> getFileExtensions() {
        return fileExtensions;
    }

    /**
     * Extensions of files, which should have the framework support enabled. Use this to support custom file extensions like '.vue' or '.svelte'. Never specify generic extensions like '.html', '.js' or '.ts'. If you need your contributions to be present in every file don't specify the framework at all
     * 
     */
    @JsonProperty("file-extensions")
    public void setFileExtensions(List<String> fileExtensions) {
        this.fileExtensions = fileExtensions;
    }

    /**
     * RegExp patterns to match file names, which should have the framework support enabled. Use carefully as broken pattern may even freeze IDE.
     * 
     */
    @JsonProperty("file-name-patterns")
    public List<Pattern> getFileNamePatterns() {
        return fileNamePatterns;
    }

    /**
     * RegExp patterns to match file names, which should have the framework support enabled. Use carefully as broken pattern may even freeze IDE.
     * 
     */
    @JsonProperty("file-name-patterns")
    public void setFileNamePatterns(List<Pattern> fileNamePatterns) {
        this.fileNamePatterns = fileNamePatterns;
    }

    /**
     * Global JavaScript libraries names enabled within the IDE, which enable framework support in the whole project
     * 
     */
    @JsonProperty("ide-libraries")
    public List<String> getIdeLibraries() {
        return ideLibraries;
    }

    /**
     * Global JavaScript libraries names enabled within the IDE, which enable framework support in the whole project
     * 
     */
    @JsonProperty("ide-libraries")
    public void setIdeLibraries(List<String> ideLibraries) {
        this.ideLibraries = ideLibraries;
    }

}
