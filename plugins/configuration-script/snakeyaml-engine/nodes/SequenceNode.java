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
package org.snakeyaml.engine.v1.nodes;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.snakeyaml.engine.v1.common.FlowStyle;
import org.snakeyaml.engine.v1.exceptions.Mark;

/**
 * Represents a sequence.
 * <p>
 * A sequence is a ordered collection of nodes.
 * </p>
 */
public class SequenceNode extends CollectionNode<Node> {
    final private List<Node> value;

    public SequenceNode(Tag tag, boolean resolved, List<Node> value,
                        FlowStyle flowStyle, Optional<Mark> startMark, Optional<Mark> endMark) {
        super(tag, flowStyle, startMark, endMark);
        Objects.requireNonNull(value, "value in a Node is required.");
        this.value = value;
        this.resolved = resolved;
    }

    public SequenceNode(Tag tag, List<Node> value, FlowStyle flowStyle) {
        this(tag, true, value, flowStyle, Optional.empty(), Optional.empty());
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.SEQUENCE;
    }

    /**
     * Returns the elements in this sequence.
     *
     * @return Nodes in the specified order.
     */
    public List<Node> getValue() {
        return value;
    }

    public List<Node> getSequence() {
        return value;
    }

    public String toString() {
        return "<" + this.getClass().getName() + " (tag=" + getTag() + ", value=" + getValue()
                + ")>";
    }
}
