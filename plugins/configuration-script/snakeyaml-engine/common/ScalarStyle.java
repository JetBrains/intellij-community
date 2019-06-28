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
package org.snakeyaml.engine.v1.common;

import java.util.Optional;

/**
 * YAML provides a rich set of scalar styles. Block scalar styles include
 * the literal style and the folded style; flow scalar styles include the
 * plain style and two quoted styles, the single-quoted style and the
 * double-quoted style. These styles offer a range of trade-offs between
 * expressive power and readability.
 */
public enum ScalarStyle {
    DOUBLE_QUOTED(Optional.of('"')),
    SINGLE_QUOTED(Optional.of('\'')),
    LITERAL(Optional.of('|')),
    FOLDED(Optional.of('>')),
    PLAIN(Optional.empty());

    private Optional<Character> styleOpt;

    ScalarStyle(Optional<Character> style) {
        this.styleOpt = style;
    }

    @Override
    public String toString() {
        if (styleOpt.isPresent()) {
            return String.valueOf(styleOpt.get());
        } else return ":";
    }
}