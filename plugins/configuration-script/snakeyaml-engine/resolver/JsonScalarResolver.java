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
package org.snakeyaml.engine.v1.resolver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.snakeyaml.engine.v1.nodes.Tag;

/**
 * ScalarResolver for JSON Schema
 * The scherma is NOT the same as in YAML 1.2 but identical to JSON,
 * 1) .inf, -.inf, .nan values are not used,
 * 2) integers may have a leading 0
 *
 * @see <a href="http://www.yaml.org/spec/1.2/spec.html#id2803231">Chapter 10.2. JSON Schema</a>
 */
public class JsonScalarResolver implements ScalarResolver {

    public static final Pattern BOOL = Pattern.compile("^(?:true|false)$");
    public static final Pattern FLOAT = Pattern.compile("^([-+]?(\\.[0-9]+|[0-9]+(\\.[0-9]*)?)([eE][-+]?[0-9]+)?)$");
    public static final Pattern INT = Pattern.compile("^(?:-?(?:0|[0-9][0-9]*))$");
    public static final Pattern MERGE = Pattern.compile("^(?:<<)$");
    public static final Pattern NULL = Pattern.compile("^(?:null)$");
    public static final Pattern EMPTY = Pattern.compile("^$");

    protected Map<Character, List<ResolverTuple>> yamlImplicitResolvers = new HashMap<Character, List<ResolverTuple>>();

    public void addImplicitResolver(Tag tag, Pattern regexp, String first) {
        if (first == null) {
            List<ResolverTuple> curr = yamlImplicitResolvers.get(null);
            if (curr == null) {
                curr = new ArrayList<ResolverTuple>();
                yamlImplicitResolvers.put(null, curr);
            }
            curr.add(new ResolverTuple(tag, regexp));
        } else {
            char[] chrs = first.toCharArray();
            for (int i = 0, j = chrs.length; i < j; i++) {
                Character theC = Character.valueOf(chrs[i]);
                if (theC == 0) {
                    // special case: for null
                    theC = null;
                }
                List<ResolverTuple> curr = yamlImplicitResolvers.get(theC);
                if (curr == null) {
                    curr = new ArrayList<ResolverTuple>();
                    yamlImplicitResolvers.put(theC, curr);
                }
                curr.add(new ResolverTuple(tag, regexp));
            }
        }
    }

    protected void addImplicitResolvers() {
        addImplicitResolver(Tag.NULL, EMPTY, null);
        addImplicitResolver(Tag.BOOL, BOOL, "tf");
        /*
         * INT must be before FLOAT because the regular expression for FLOAT
         * matches INT (see issue 130)
         * http://code.google.com/p/snakeyaml/issues/detail?id=130
         */
        addImplicitResolver(Tag.INT, INT, "-0123456789");
        addImplicitResolver(Tag.FLOAT, FLOAT, "-+0123456789.");
        addImplicitResolver(Tag.MERGE, MERGE, "<");
        addImplicitResolver(Tag.NULL, NULL, "n\u0000");
    }

    public JsonScalarResolver() {
        addImplicitResolvers();
    }

    @Override
    public Tag resolve(String value, Boolean implicit) {
        if (implicit) {
            final List<ResolverTuple> resolvers;
            if (value.length() == 0) {
                resolvers = yamlImplicitResolvers.get('\0');
            } else {
                resolvers = yamlImplicitResolvers.get(value.charAt(0));
            }
            if (resolvers != null) {
                for (ResolverTuple v : resolvers) {
                    Tag tag = v.getTag();
                    Pattern regexp = v.getRegexp();
                    if (regexp.matcher(value).matches()) {
                        return tag;
                    }
                }
            }
            if (yamlImplicitResolvers.containsKey(null)) {
                for (ResolverTuple v : yamlImplicitResolvers.get(null)) {
                    Tag tag = v.getTag();
                    Pattern regexp = v.getRegexp();
                    if (regexp.matcher(value).matches()) {
                        return tag;
                    }
                }
            }
        }
        return Tag.STR;
    }
}
