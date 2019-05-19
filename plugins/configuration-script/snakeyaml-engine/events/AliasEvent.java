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
package org.snakeyaml.engine.v1.events;

import java.util.Optional;

import org.snakeyaml.engine.v1.common.Anchor;
import org.snakeyaml.engine.v1.exceptions.Mark;

/**
 * Marks the inclusion of a previously anchored node.
 */
public final class AliasEvent extends NodeEvent {
    public AliasEvent(Optional<Anchor> anchor, Optional<Mark> startMark, Optional<Mark> endMark) {
        super(anchor, startMark, endMark);
        if (!anchor.isPresent()) throw new NullPointerException("Anchor is required in AliasEvent");
    }

    public AliasEvent(Optional<Anchor> anchor) {
        this(anchor, Optional.empty(), Optional.empty());
    }

    @Override
    public ID getEventId() {
        return ID.Alias;
    }

    @Override
    public String toString() {
        return "=ALI *" + getAnchor().get();
    }
}
