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


import java.util.Objects;
import java.util.Optional;

import org.snakeyaml.engine.v1.common.Anchor;
import org.snakeyaml.engine.v1.exceptions.Mark;

/**
 * Base class for all events that mark the beginning of a node.
 */
public abstract class NodeEvent extends Event {

    private final Optional<Anchor> anchor;

    public NodeEvent(Optional<Anchor> anchor, Optional<Mark> startMark, Optional<Mark> endMark) {
        super(startMark, endMark);
        Objects.requireNonNull(anchor, "Anchor cannot be null");
        this.anchor = anchor;
    }

    /**
     * Node anchor by which this node might later be referenced by a
     * {@link AliasEvent}.
     * <p>
     * Note that {@link AliasEvent}s are by it self <code>NodeEvent</code>s and
     * use this property to indicate the referenced anchor.
     *
     * @return Anchor of this node or <code>null</code> if no anchor is defined.
     */
    public Optional<Anchor> getAnchor() {
        return this.anchor;
    }
}
