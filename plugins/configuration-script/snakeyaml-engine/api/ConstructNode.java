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

import org.snakeyaml.engine.v1.exceptions.YamlEngineException;
import org.snakeyaml.engine.v1.nodes.Node;

/**
 * Provide a way to construct a Java instance from the composed Node. Support
 * recursive objects if it is required. (create Native Data Structure out of
 * Node Graph)
 * (this is the opposite for Represent)
 *
 * @see <a href="http://www.yaml.org/spec/1.2/spec.html#id2762107">Processing Overview</a>
 */
public interface ConstructNode {

    /**
     * Construct a Java instance with all the properties injected when it is possible.
     *
     * @param node composed Node
     * @return a complete Java instance or empty collection instance if it is recursive
     */
    Object construct(Node node);

    /**
     * Apply the second step when constructing recursive structures. Because the
     * instance is already created it can assign a reference to itself.
     * (no need to implement this method for non-recursive data structures).
     * Fail with a reminder to provide the seconds step for a recursive
     * structure
     *
     * @param node   composed Node
     * @param object the instance constructed earlier by
     *               <code>construct(Node node)</code> for the provided Node
     */
    default void constructRecursive(Node node, Object object) {
        if (node.isRecursive()) {
            throw new IllegalStateException("Not implemented in " + getClass().getName());
        } else {
            throw new YamlEngineException("Unexpected recursive structure for Node: " + node);
        }
    }
}
