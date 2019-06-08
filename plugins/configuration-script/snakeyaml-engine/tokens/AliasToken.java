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

import org.snakeyaml.engine.v1.common.Anchor;
import org.snakeyaml.engine.v1.exceptions.Mark;

public final class AliasToken extends Token {
    private final Anchor value;

    public AliasToken(Anchor value, Optional<Mark> startMark, Optional<Mark> endMark) {
        super(startMark, endMark);
        if (value == null) throw new NullPointerException("Value is required in AliasToken");
        this.value = value;
    }

    public Anchor getValue() {
        return this.value;
    }

    @Override
    public Token.ID getTokenId() {
        return ID.Alias;
    }
}
