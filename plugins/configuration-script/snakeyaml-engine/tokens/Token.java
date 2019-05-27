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
package org.snakeyaml.engine.v1.tokens;

import java.util.Objects;
import java.util.Optional;

import org.snakeyaml.engine.v1.exceptions.Mark;

public abstract class Token {
    public enum ID {
        Alias("<alias>"),
        Anchor("<anchor>"),
        BlockEnd("<block end>"),
        BlockEntry("-"),
        BlockMappingStart("<block mapping start>"),
        BlockSequenceStart("<block sequence start>"),
        Directive("<directive>"),
        DocumentEnd("<document end>"),
        DocumentStart("<document start>"),
        FlowEntry(","),
        FlowMappingEnd("}"),
        FlowMappingStart("{"),
        FlowSequenceEnd("]"),
        FlowSequenceStart("["),
        Key("?"),
        Scalar("<scalar>"),
        StreamEnd("<stream end>"),
        StreamStart("<stream start>"),
        Tag("<tag>"),
        Value(":");

        private final String description;

        ID(String s) {
            description = s;
        }

        @Override
        public String toString() {
            return description;
        }
    }

    private final Optional<Mark> startMark;
    private final Optional<Mark> endMark;

    public Token(Optional<Mark> startMark, Optional<Mark> endMark) {
        Objects.requireNonNull(startMark);
        Objects.requireNonNull(endMark);
        this.startMark = startMark;
        this.endMark = endMark;
    }

    public Optional<Mark> getStartMark() {
        return startMark;
    }

    public Optional<Mark> getEndMark() {
        return endMark;
    }

    /**
     * For error reporting.
     *
     * @return ID of this token
     */
    public abstract Token.ID getTokenId();

    @Override
    public String toString() {
        return getTokenId().toString();
    }
}
