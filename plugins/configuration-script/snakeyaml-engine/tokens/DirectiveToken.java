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


import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.snakeyaml.engine.v1.exceptions.Mark;
import org.snakeyaml.engine.v1.exceptions.YamlEngineException;

public final class DirectiveToken<T> extends Token {
    private final String name;
    private final Optional<List<T>> value;

    public static final String YAML_DIRECTIVE = "YAML";
    public static final String TAG_DIRECTIVE = "TAG";

    public DirectiveToken(String name, Optional<List<T>> value, Optional<Mark> startMark, Optional<Mark> endMark) {
        super(startMark, endMark);
        this.name = name;
        Objects.requireNonNull(value);
        if (value.isPresent() && value.get().size() != 2) {
            throw new YamlEngineException("Two strings/integers must be provided instead of "
                    + String.valueOf(value.get().size()));
        }
        this.value = value;
    }

    public String getName() {
        return this.name;
    }

    public Optional<List<T>> getValue() {
        return this.value;
    }

    @Override
    public Token.ID getTokenId() {
        return ID.Directive;
    }
}
