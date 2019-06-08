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

import com.intellij.ide.fileTemplates.impl.UrlUtil;
import com.intellij.util.io.URLUtil;
import org.snakeyaml.engine.v1.common.UriEncoder;

public final class Tag {
    public static final String PREFIX = "tag:yaml.org,2002:";
    public static final Tag MERGE = new Tag(PREFIX + "merge");
    public static final Tag SET = new Tag(PREFIX + "set");
    public static final Tag BINARY = new Tag(PREFIX + "binary");
    public static final Tag INT = new Tag(PREFIX + "int");
    public static final Tag FLOAT = new Tag(PREFIX + "float");
    public static final Tag BOOL = new Tag(PREFIX + "bool");
    public static final Tag NULL = new Tag(PREFIX + "null");
    public static final Tag STR = new Tag(PREFIX + "str");
    public static final Tag SEQ = new Tag(PREFIX + "seq");
    public static final Tag MAP = new Tag(PREFIX + "map");

    private final String value;

    public Tag(String tag) {
        Objects.requireNonNull(tag, "Tag must be provided.");
        if (tag.isEmpty()) {
            throw new IllegalArgumentException("Tag must not be empty.");
        } else if (tag.trim().length() != tag.length()) {
            throw new IllegalArgumentException("Tag must not contain leading or trailing spaces.");
        }
        this.value = URLUtil.encodeURIComponent(tag);
    }

    /**
     * Create a global tag to dump the fully qualified class name
     *
     * @param clazz - the class to use the name
     */
    public Tag(Class<? extends Object> clazz) {
        Objects.requireNonNull(clazz, "Class for tag must be provided.");
        this.value = Tag.PREFIX + URLUtil.encodeURIComponent(clazz.getName());
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Tag) {
            return value.equals(((Tag) obj).getValue());
        } else
            return false;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}

