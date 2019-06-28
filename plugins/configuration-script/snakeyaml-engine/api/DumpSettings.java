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

import java.util.Map;
import java.util.Optional;

import org.snakeyaml.engine.v1.common.FlowStyle;
import org.snakeyaml.engine.v1.common.NonPrintableStyle;
import org.snakeyaml.engine.v1.common.ScalarStyle;
import org.snakeyaml.engine.v1.common.SpecVersion;
import org.snakeyaml.engine.v1.nodes.Tag;
import org.snakeyaml.engine.v1.resolver.ScalarResolver;
import org.snakeyaml.engine.v1.serializer.AnchorGenerator;

/**
 * Fine tuning serializing/dumping
 * Description for all the fields can be found in the builder
 */
public final class DumpSettings {
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


    DumpSettings(boolean explicitStart, boolean explicitEnd, Optional<Tag> explicitRootTag,
                 AnchorGenerator anchorGenerator, Optional<SpecVersion> yamlDirective, Map<String, String> tagDirective,
                 ScalarResolver scalarResolver, FlowStyle defaultFlowStyle, ScalarStyle defaultScalarStyle,
                 NonPrintableStyle nonPrintableStyle,
                 //emitter
                 boolean canonical, boolean multiLineFlow, boolean useUnicodeEncoding,
                 int indent, int indicatorIndent, int width, String bestLineBreak, boolean splitLines, int maxSimpleKeyLength
    ) {
        this.explicitStart = explicitStart;
        this.explicitEnd = explicitEnd;
        this.nonPrintableStyle = nonPrintableStyle;
        this.explicitRootTag = explicitRootTag;
        this.anchorGenerator = anchorGenerator;
        this.yamlDirective = yamlDirective;
        this.tagDirective = tagDirective;
        this.scalarResolver = scalarResolver;
        this.defaultFlowStyle = defaultFlowStyle;
        this.defaultScalarStyle = defaultScalarStyle;
        this.canonical = canonical;
        this.multiLineFlow = multiLineFlow;
        this.useUnicodeEncoding = useUnicodeEncoding;
        this.indent = indent;
        this.indicatorIndent = indicatorIndent;
        this.width = width;
        this.bestLineBreak = bestLineBreak;
        this.splitLines = splitLines;
        this.maxSimpleKeyLength = maxSimpleKeyLength;
    }


    public FlowStyle getDefaultFlowStyle() {
        return defaultFlowStyle;
    }

    public ScalarStyle getDefaultScalarStyle() {
        return defaultScalarStyle;
    }

    public boolean isExplicitStart() {
        return explicitStart;
    }

    public AnchorGenerator getAnchorGenerator() {
        return anchorGenerator;
    }

    public ScalarResolver getScalarResolver() {
        return scalarResolver;
    }

    public boolean isExplicitEnd() {
        return explicitEnd;
    }

    public Optional<Tag> getExplicitRootTag() {
        return explicitRootTag;
    }

    public Optional<SpecVersion> getYamlDirective() {
        return yamlDirective;
    }

    public Map<String, String> getTagDirective() {
        return tagDirective;
    }

    public boolean isCanonical() {
        return canonical;
    }

    public boolean isMultiLineFlow() {
        return multiLineFlow;
    }

    public boolean isUseUnicodeEncoding() {
        return useUnicodeEncoding;
    }

    public int getIndent() {
        return indent;
    }

    public int getIndicatorIndent() {
        return indicatorIndent;
    }

    public int getWidth() {
        return width;
    }

    public String getBestLineBreak() {
        return bestLineBreak;
    }

    public boolean isSplitLines() {
        return splitLines;
    }

    public int getMaxSimpleKeyLength() {
        return maxSimpleKeyLength;
    }

    public NonPrintableStyle getNonPrintableStyle() {
        return nonPrintableStyle;
    }
}

