/**
 * Copyright (c) 2018, http://www.snakeyaml.org
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.snakeyaml.engine.v1.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.snakeyaml.engine.v1.common.FlowStyle;
import org.snakeyaml.engine.v1.common.NonPrintableStyle;
import org.snakeyaml.engine.v1.common.ScalarStyle;
import org.snakeyaml.engine.v1.common.SpecVersion;
import org.snakeyaml.engine.v1.emitter.Emitter;
import org.snakeyaml.engine.v1.exceptions.EmitterException;
import org.snakeyaml.engine.v1.exceptions.YamlEngineException;
import org.snakeyaml.engine.v1.nodes.Tag;
import org.snakeyaml.engine.v1.resolver.JsonScalarResolver;
import org.snakeyaml.engine.v1.resolver.ScalarResolver;
import org.snakeyaml.engine.v1.serializer.AnchorGenerator;
import org.snakeyaml.engine.v1.serializer.NumberAnchorGenerator;

/**
 * Builder pattern implementation for DumpSettings
 */
public final class DumpSettingsBuilder {
    private boolean explicitStart;
    private boolean explicitEnd;
    private NonPrintableStyle nonPrintableStyle;
    private Optional<Tag> explicitRootTag;
    private AnchorGenerator anchorGenerator;
    private Optional<SpecVersion> yamlDirective;
    private Map<String, String> tagDirective;
    private ScalarResolver scalarResolver;
    private FlowStyle defaultFlowStyle;
    private ScalarStyle defaultScalarStyle;

    //emitter
    private boolean canonical;
    private boolean multiLineFlow;
    private boolean useUnicodeEncoding;
    private int indent;
    private int indicatorIndent;
    private int width;
    private String bestLineBreak;
    private boolean splitLines;
    private int maxSimpleKeyLength;


    /**
     * Create builder
     */
    public DumpSettingsBuilder() {
        this.explicitRootTag = Optional.empty();
        this.tagDirective = new HashMap<>();
        this.scalarResolver = new JsonScalarResolver();
        this.anchorGenerator = new NumberAnchorGenerator(1);
        this.bestLineBreak = "\n";
        this.canonical = false;
        this.useUnicodeEncoding = true;
        this.indent = 2;
        this.indicatorIndent = 0;
        this.width = 80;
        this.splitLines = true;
        this.explicitStart = false;
        this.explicitEnd = false;
        this.yamlDirective = Optional.empty();
        this.defaultFlowStyle = FlowStyle.AUTO;
        this.defaultScalarStyle = ScalarStyle.PLAIN;
        this.maxSimpleKeyLength = 128;
    }

    /**
     * Define flow style
     * @param defaultFlowStyle - specify the style
     * @return the builder with the provided value
     */
    public DumpSettingsBuilder setDefaultFlowStyle(FlowStyle defaultFlowStyle) {
        this.defaultFlowStyle = defaultFlowStyle;
        return this;
    }

    /**
     * Define default scalar style
     * @param defaultScalarStyle - specify the scalar style
     * @return the builder with the provided value
     */
    public DumpSettingsBuilder setDefaultScalarStyle(ScalarStyle defaultScalarStyle) {
        this.defaultScalarStyle = defaultScalarStyle;
        return this;
    }

    /**
     * Add '---' in the beginning of the document
     * @param explicitStart - true if the document start must be explicitly indicated
     * @return the builder with the provided value
     */
    public DumpSettingsBuilder setExplicitStart(boolean explicitStart) {
        this.explicitStart = explicitStart;
        return this;
    }

    /**
     * Define anchor name generator (by default 'id' + number)
     * @param anchorGenerator - specified function to create anchor names
     * @return the builder with the provided value
     */
    public DumpSettingsBuilder setAnchorGenerator(AnchorGenerator anchorGenerator) {
        Objects.requireNonNull(anchorGenerator, "anchorGenerator cannot be null");
        this.anchorGenerator = anchorGenerator;
        return this;
    }

    /**
     * Define {@link ScalarResolver} or use JSON resolver by default
     * @param scalarResolver - specify the scalar resolver
     * @return the builder with the provided value
     */
    public DumpSettingsBuilder setScalarResolver(ScalarResolver scalarResolver) {
        Objects.requireNonNull(scalarResolver, "scalarResolver cannot be null");
        this.scalarResolver = scalarResolver;
        return this;
    }

    /**
     * Define root {@link Tag} or let the tag to be detected automatically
     * @param explicitRootTag - specify the root tag
     * @return the builder with the provided value
     */
    public DumpSettingsBuilder setExplicitRootTag(Optional<Tag> explicitRootTag) {
        Objects.requireNonNull(explicitRootTag, "explicitRootTag cannot be null");
        this.explicitRootTag = explicitRootTag;
        return this;
    }

    /**
     *  Add '...' in the end of the document
     * @param explicitEnd - true if the document end must be explicitly indicated
     * @return the builder with the provided value
     */
    public DumpSettingsBuilder setExplicitEnd(boolean explicitEnd) {
        this.explicitEnd = explicitEnd;
        return this;
    }

    /**
     * Add YAML directive (http://yaml.org/spec/1.2/spec.html#id2781553)
     * @param yamlDirective - the version to be used in the directive
     * @return the builder with the provided value
     */
    public DumpSettingsBuilder setYamlDirective(Optional<SpecVersion> yamlDirective) {
        Objects.requireNonNull(yamlDirective, "yamlDirective cannot be null");
        this.yamlDirective = yamlDirective;
        return this;
    }

    /**
     * Add TAG directive (http://yaml.org/spec/1.2/spec.html#id2782090)
     * @param tagDirective - the data to create TAG directive
     * @return the builder with the provided value
     */
    public DumpSettingsBuilder setTagDirective(Map<String, String> tagDirective) {
        Objects.requireNonNull(tagDirective, "tagDirective cannot be null");
        this.tagDirective = tagDirective;
        return this;
    }

    /**
     * Enforce canonical representation
     * @param canonical - specify if the canonical representation must be used
     * @return the builder with the provided value
     */
    public DumpSettingsBuilder setCanonical(boolean canonical) {
        this.canonical = canonical;
        return this;
    }

    /**
     * Use pretty flow style when every value in the flow context gets a separate line.
     * @param multiLineFlow - set false to output all values in a single line.
     * @return the builder with the provided value
     */
    public DumpSettingsBuilder setMultiLineFlow(boolean multiLineFlow) {
        this.multiLineFlow = multiLineFlow;
        return this;
    }

    /**
     * Define whether Unicode char or escape sequence starting with '\\u'
     * @param useUnicodeEncoding - true to use Unicode for "�", false to use "\ufffd" for the same char
     * @return the builder with the provided value
     */
    public DumpSettingsBuilder setUseUnicodeEncoding(boolean useUnicodeEncoding) {
        this.useUnicodeEncoding = useUnicodeEncoding;
        return this;
    }

    /**
     * Define the amount of the spaces for the indent in the block flow style. Default is 2.
     * @param indent - the number of spaces. Must be within the range org.snakeyaml.engine.v1.emitter.Emitter.MIN_INDENT
     *               and org.snakeyaml.engine.v1.emitter.Emitter.MAX_INDENT
     * @return the builder with the provided value
     */
    public DumpSettingsBuilder setIndent(int indent) {
        if (indent < Emitter.MIN_INDENT) {
            throw new EmitterException("Indent must be at least " + Emitter.MIN_INDENT);
        }
        if (indent > Emitter.MAX_INDENT) {
            throw new EmitterException("Indent must be at most " + Emitter.MAX_INDENT);
        }
        this.indent = indent;
        return this;
    }

    /**
     * Default is 0.
     * @param indicatorIndent - must be non-negative and less than org.snakeyaml.engine.v1.emitter.Emitter.MAX_INDENT - 1
     * @return the builder with the provided value
     */
    public DumpSettingsBuilder setIndicatorIndent(int indicatorIndent) {
        if (indicatorIndent < 0) {
            throw new EmitterException("Indicator indent must be non-negative.");
        }
        if (indicatorIndent > Emitter.MAX_INDENT - 1) {
            throw new EmitterException("Indicator indent must be at most Emitter.MAX_INDENT-1: " + (Emitter.MAX_INDENT - 1));
        }
        this.indicatorIndent = indicatorIndent;
        return this;
    }

    /**
     * Set max width for literal scalars. When the scalar
     * representation takes more then the preferred with the scalar will be
     * split into a few lines. The default is 80.
     * @param width - the width
     * @return the builder with the provided value
     */
    public DumpSettingsBuilder setWidth(int width) {
        this.width = width;
        return this;
    }

    /**
     * If the YAML is created for another platform (for instance on Windows to be consumed under Linux) than
     * this setting is used to define the line ending. The platform line end is used by default.
     * @param bestLineBreak -  "\r\n" or "\n"
     * @return the builder with the provided value
     */
    public DumpSettingsBuilder setBestLineBreak(String bestLineBreak) {
        Objects.requireNonNull(bestLineBreak, "bestLineBreak cannot be null");
        this.bestLineBreak = bestLineBreak;
        return this;
    }

    /**
     * Define whether to split long lines
     * @param splitLines - true to split long lines
     * @return the builder with the provided value
     */
    public DumpSettingsBuilder setSplitLines(boolean splitLines) {
        this.splitLines = splitLines;
        return this;
    }

    /**
     * Define max key length to use simple key (without '?')
     * More info https://yaml.org/spec/1.1/#id934537
     * @param maxSimpleKeyLength - the limit after which the key gets explicit key indicator '?'
     * @return the builder with the provided value
     */
    public DumpSettingsBuilder setMaxSimpleKeyLength(int maxSimpleKeyLength) {
        if(maxSimpleKeyLength > 1024) {
            throw new YamlEngineException("The simple key must not span more than 1024 stream characters. See https://yaml.org/spec/1.1/#id934537");
        }
        this.maxSimpleKeyLength = maxSimpleKeyLength;
        return this;
    }

    /**
     * Create immutable DumpSettings
     * @return DumpSettings with the provided values
     */
    public DumpSettings build() {
        return new DumpSettings(explicitStart, explicitEnd, explicitRootTag,
                anchorGenerator, yamlDirective, tagDirective,
                scalarResolver, defaultFlowStyle, defaultScalarStyle, nonPrintableStyle,
                //emitter
                canonical, multiLineFlow, useUnicodeEncoding,
                indent, indicatorIndent, width, bestLineBreak, splitLines, maxSimpleKeyLength);
    }

    /**
     * When String object contains non-printable characters, they are escaped with \\u or \\x notation.
     * Sometimes it is better to transform this data to binary (with the !!binary tag).
     * String objects with printable data are non affected by this setting.
     * @param nonPrintableStyle - set this to BINARY to force non-printable String to represented as binary (byte array)
     * @return the builder with the provided value
     */
    public DumpSettingsBuilder setNonPrintableStyle(NonPrintableStyle nonPrintableStyle) {
        this.nonPrintableStyle = nonPrintableStyle;
        return this;
    }
}

