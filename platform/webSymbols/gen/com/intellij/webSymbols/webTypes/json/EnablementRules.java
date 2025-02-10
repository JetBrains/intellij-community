
package com.intellij.webSymbols.webTypes.json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
    "ruby-gems",
    "symfony-bundles",
    "file-extensions",
    "file-name-patterns",
    "ide-libraries",
    "project-tool-executables"
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
     * Ruby gem names, which enable framework support within the particular Ruby module.
     * 
     */
    @JsonProperty("ruby-gems")
    @JsonPropertyDescription("Ruby gem names, which enable framework support within the particular Ruby module.")
    private List<String> rubyGems = new ArrayList<String>();
    /**
     * Symfony bundle names, which enable framework support within the particular Symfony project using AssetMapper.
     * 
     */
    @JsonProperty("symfony-bundles")
    @JsonPropertyDescription("Symfony bundle names, which enable framework support within the particular Symfony project using AssetMapper.")
    private List<String> symfonyBundles = new ArrayList<String>();
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
     * List of tool executables (without extension), which presence should be checked in the project. In case of Node projects, such tools will be searched in node_modules/.bin/
     * 
     */
    @JsonProperty("project-tool-executables")
    @JsonPropertyDescription("List of tool executables (without extension), which presence should be checked in the project. In case of Node projects, such tools will be searched in node_modules/.bin/")
    private List<String> projectToolExecutables = new ArrayList<String>();
    @JsonIgnore
    private Map<String, List<String>> additionalProperties = new HashMap<String, List<String>>();

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
     * Ruby gem names, which enable framework support within the particular Ruby module.
     * 
     */
    @JsonProperty("ruby-gems")
    public List<String> getRubyGems() {
        return rubyGems;
    }

    /**
     * Ruby gem names, which enable framework support within the particular Ruby module.
     * 
     */
    @JsonProperty("ruby-gems")
    public void setRubyGems(List<String> rubyGems) {
        this.rubyGems = rubyGems;
    }

    /**
     * Symfony bundle names, which enable framework support within the particular Symfony project using AssetMapper.
     * 
     */
    @JsonProperty("symfony-bundles")
    public List<String> getSymfonyBundles() {
        return symfonyBundles;
    }

    /**
     * Symfony bundle names, which enable framework support within the particular Symfony project using AssetMapper.
     * 
     */
    @JsonProperty("symfony-bundles")
    public void setSymfonyBundles(List<String> symfonyBundles) {
        this.symfonyBundles = symfonyBundles;
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

    /**
     * List of tool executables (without extension), which presence should be checked in the project. In case of Node projects, such tools will be searched in node_modules/.bin/
     * 
     */
    @JsonProperty("project-tool-executables")
    public List<String> getProjectToolExecutables() {
        return projectToolExecutables;
    }

    /**
     * List of tool executables (without extension), which presence should be checked in the project. In case of Node projects, such tools will be searched in node_modules/.bin/
     * 
     */
    @JsonProperty("project-tool-executables")
    public void setProjectToolExecutables(List<String> projectToolExecutables) {
        this.projectToolExecutables = projectToolExecutables;
    }

    @JsonAnyGetter
    public Map<String, List<String>> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, List<String> value) {
        this.additionalProperties.put(name, value);
    }

}
