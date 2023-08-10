
package com.intellij.webSymbols.webTypes.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "file",
    "offset"
})
public class SourceFileOffset
    extends SourceBase
{

    /**
     * Path to the file, relative to the web-types JSON.
     * (Required)
     * 
     */
    @JsonProperty("file")
    @JsonPropertyDescription("Path to the file, relative to the web-types JSON.")
    private String file;
    /**
     * Offset in the file under which the source symbol, like class name, is located.
     * (Required)
     * 
     */
    @JsonProperty("offset")
    @JsonPropertyDescription("Offset in the file under which the source symbol, like class name, is located.")
    private Integer offset;

    /**
     * Path to the file, relative to the web-types JSON.
     * (Required)
     * 
     */
    @JsonProperty("file")
    public String getFile() {
        return file;
    }

    /**
     * Path to the file, relative to the web-types JSON.
     * (Required)
     * 
     */
    @JsonProperty("file")
    public void setFile(String file) {
        this.file = file;
    }

    /**
     * Offset in the file under which the source symbol, like class name, is located.
     * (Required)
     * 
     */
    @JsonProperty("offset")
    public Integer getOffset() {
        return offset;
    }

    /**
     * Offset in the file under which the source symbol, like class name, is located.
     * (Required)
     * 
     */
    @JsonProperty("offset")
    public void setOffset(Integer offset) {
        this.offset = offset;
    }

}
