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


import java.util.Objects;
import java.util.Optional;

import org.snakeyaml.engine.v1.api.ConstructNode;
import org.snakeyaml.engine.v1.common.Anchor;
import org.snakeyaml.engine.v1.exceptions.Mark;

/**
 * Base class for all nodes.
 * <p>
 * The nodes form the node-graph described in the <a
 * href="http://yaml.org/spec/1.1/">YAML Specification</a>.
 * </p>
 * <p>
 * While loading, the node graph is usually created by the
 * {@link org.snakeyaml.engine.v1.composer.Composer}.
 * </p>
 */
public abstract class Node {
    private Tag tag;
    private Optional<Mark> startMark;
    protected Optional<Mark> endMark;
    private boolean recursive;
    private Optional<ConstructNode> construct;
    private Optional<Anchor> anchor;

    /**
     * true when the tag is assigned by the resolver
     */
    protected boolean resolved;

    /**
     * Create Node to be parsed
     *
     * @param tag       - the tag
     * @param startMark - start mark when available
     * @param endMark   - end mark when available
     */
    public Node(Tag tag, Optional<Mark> startMark, Optional<Mark> endMark) {
        setTag(tag);
        this.startMark = startMark;
        this.endMark = endMark;
        this.recursive = false;
        this.resolved = true;
        this.construct = Optional.empty();
        this.anchor = Optional.empty();
    }

    /**
     * Tag of this node.
     * <p>
     * Every node has a tag assigned. The tag is either local or global.
     *
     * @return Tag of this node.
     */
    public Tag getTag() {
        return this.tag;
    }

    public Optional<Mark> getEndMark() {
        return endMark;
    }

    /**
     * @return scalar, sequence, mapping
     */
    public abstract NodeType getNodeType();

    public Optional<Mark> getStartMark() {
        return startMark;
    }

    public void setTag(Tag tag) {
        Objects.requireNonNull(tag, "tag in a Node is required.");
        this.tag = tag;
    }

    /**
     * Node is only equal to itself
     */
    @Override
    public final boolean equals(Object obj) {
        return super.equals(obj);
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    /**
     * Indicates if this node must be constructed in two steps.
     * <p>
     * Two-step construction is required whenever a node is a child (direct or
     * indirect) of it self. That is, if a recursive structure is build using
     * anchors and aliases.
     * </p>
     * <p>
     * Set by {@link org.snakeyaml.engine.v1.composer.Composer}, used during the
     * construction process.
     * </p>
     * <p>
     * Only relevant during loading.
     * </p>
     *
     * @return <code>true</code> if the node is self referenced.
     */
    public boolean isRecursive() {
        return recursive;
    }

    @Override
    public final int hashCode() {
        return super.hashCode();
    }

    public Optional<ConstructNode> getConstruct() {
        return construct;
    }

    public void setConstruct(ConstructNode construct) {
        this.construct = Optional.of(construct);
    }

    public Optional<Anchor> getAnchor() {
        return anchor;
    }

    public void setAnchor(Optional<Anchor> anchor) {
        this.anchor = anchor;
    }
}
