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

import java.util.Optional;

import org.snakeyaml.engine.v1.common.ScalarStyle;
import org.snakeyaml.engine.v1.exceptions.Mark;

public final class ScalarToken extends Token {
    private final String value;
    private final boolean plain;
    private final ScalarStyle style;

    public ScalarToken(String value, boolean plain, Optional<Mark> startMark, Optional<Mark> endMark) {
        this(value, plain, ScalarStyle.PLAIN, startMark, endMark);
    }

    public ScalarToken(String value, boolean plain, ScalarStyle style, Optional<Mark> startMark, Optional<Mark> endMark) {
        super(startMark, endMark);
        this.value = value;
        this.plain = plain;
        if (style == null) throw new NullPointerException("Style must be provided.");
        this.style = style;
    }

    public boolean isPlain() {
        return this.plain;
    }

    public String getValue() {
        return this.value;
    }

    public ScalarStyle getStyle() {
        return this.style;
    }

    @Override
    public Token.ID getTokenId() {
        return ID.Scalar;
    }
}
