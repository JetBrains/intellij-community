/*
 * Copyright 2005 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.xpath.context.functions;

import com.intellij.openapi.util.Pair;

import org.apache.commons.collections.map.CompositeMap;
import org.intellij.lang.xpath.context.ContextType;
import org.intellij.lang.xpath.psi.XPathType;

import javax.xml.namespace.QName;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultFunctionContext implements FunctionContext {
    private static final Map<QName, Function> DEFAULT_FUNCTIONS;
    private static final Map<ContextType, DefaultFunctionContext> ourInstances = new HashMap<ContextType, DefaultFunctionContext>();

    private final Map<QName, Function> myFunctions;

    protected DefaultFunctionContext(ContextType contextType) {
        assert !ourInstances.containsKey(contextType);

        myFunctions = Collections.<QName, Function>unmodifiableMap(new CompositeMap(DEFAULT_FUNCTIONS, getAdditionalFunctions(contextType)));
        ourInstances.put(contextType, this);
    }

    protected Map<QName, Function> getAdditionalFunctions(ContextType contextType) {
        return getProvidedFunctions(contextType);
    }

    static {
        final Map<QName, Function> decls = new HashMap<QName, Function>();

        addFunction(decls, "last", new Function(XPathType.NUMBER));
        addFunction(decls, "position", new Function(XPathType.NUMBER));
        addFunction(decls, "count", new Function(XPathType.NUMBER,
                new Parameter(XPathType.NODESET, Parameter.Kind.REQUIRED)
        ));
        addFunction(decls, "id", new Function(XPathType.STRING,
                new Parameter(XPathType.ANY, Parameter.Kind.REQUIRED)
        ));
        addFunction(decls, "local-name", new Function(XPathType.STRING,
                new Parameter(XPathType.NODESET, Parameter.Kind.OPTIONAL)
        ));
        addFunction(decls, "namespace-uri", new Function(XPathType.STRING,
                new Parameter(XPathType.NODESET, Parameter.Kind.OPTIONAL)
        ));
        addFunction(decls, "name", new Function(XPathType.STRING,
                new Parameter(XPathType.NODESET, Parameter.Kind.OPTIONAL)
        ));

        addFunction(decls, "string", new Function(XPathType.STRING,
                new Parameter(XPathType.ANY, Parameter.Kind.OPTIONAL)
        ));
        addFunction(decls, "concat", new Function(XPathType.STRING,
                new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED),
                new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED),
                new Parameter(XPathType.STRING, Parameter.Kind.VARARG)
        ));
        addFunction(decls, "starts-with", new Function(XPathType.BOOLEAN,
                new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED),
                new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED)
        ));
        addFunction(decls, "contains", new Function(XPathType.BOOLEAN,
                new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED),
                new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED)
        ));
        addFunction(decls, "substring-before", new Function(XPathType.STRING,
                new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED),
                new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED)
        ));
        addFunction(decls, "substring-after", new Function(XPathType.STRING,
                new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED),
                new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED)
        ));
        addFunction(decls, "substring", new Function(XPathType.STRING,
                new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED),
                new Parameter(XPathType.NUMBER, Parameter.Kind.REQUIRED),
                new Parameter(XPathType.NUMBER, Parameter.Kind.OPTIONAL)
        ));
        addFunction(decls, "string-length", new Function(XPathType.NUMBER,
                new Parameter(XPathType.STRING, Parameter.Kind.OPTIONAL)
        ));
        addFunction(decls, "normalize-space", new Function(XPathType.STRING,
                new Parameter(XPathType.STRING, Parameter.Kind.OPTIONAL)
        ));
        addFunction(decls, "translate", new Function(XPathType.STRING,
                new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED),
                new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED),
                new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED)
        ));

        addFunction(decls, "boolean", new Function(XPathType.BOOLEAN,
                new Parameter(XPathType.ANY, Parameter.Kind.REQUIRED)
        ));
        addFunction(decls, "not", new Function(XPathType.BOOLEAN,
                new Parameter(XPathType.BOOLEAN, Parameter.Kind.REQUIRED)
        ));
        addFunction(decls, "true", new Function(XPathType.BOOLEAN));
        addFunction(decls, "false", new Function(XPathType.BOOLEAN));
        addFunction(decls, "lang", new Function(XPathType.BOOLEAN,
                new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED)
        ));

        addFunction(decls, "number", new Function(XPathType.NUMBER,
                new Parameter(XPathType.ANY, Parameter.Kind.OPTIONAL)
        ));
        addFunction(decls, "sum", new Function(XPathType.NUMBER,
                new Parameter(XPathType.NODESET, Parameter.Kind.REQUIRED)
        ));
        addFunction(decls, "floor", new Function(XPathType.NUMBER,
                new Parameter(XPathType.NUMBER, Parameter.Kind.REQUIRED)
        ));
        addFunction(decls, "ceiling", new Function(XPathType.NUMBER,
                new Parameter(XPathType.NUMBER, Parameter.Kind.REQUIRED)
        ));
        addFunction(decls, "round", new Function(XPathType.NUMBER,
                new Parameter(XPathType.NUMBER, Parameter.Kind.REQUIRED)
        ));

        // XSLT
        addFunction(decls, "document", new Function(XPathType.NODESET,
                new Parameter(XPathType.ANY, Parameter.Kind.REQUIRED),
                new Parameter(XPathType.NODESET, Parameter.Kind.OPTIONAL)
        ));

        DEFAULT_FUNCTIONS = Collections.unmodifiableMap(decls);
    }

    private static Map<QName, Function> getProvidedFunctions(ContextType contextType) {
        final Map<QName, Function> map = new HashMap<QName, Function>();
        final List<Pair<QName,? extends Function>> availableFunctions = XPathFunctionProvider.getAvailableFunctions(contextType);
        for (Pair<QName, ? extends Function> pair : availableFunctions) {
            map.put(pair.first, pair.second);
        }
        return Collections.unmodifiableMap(map);
    }

    protected static void addFunction(Map<QName, Function> decls, String key, Function value) {
        decls.put(new QName(null, key), value);
    }

    public Map<QName, Function> getFunctions() {
        return myFunctions;
    }

    public boolean allowsExtensions() {
        return false;
    }

    public static synchronized DefaultFunctionContext getInstance(ContextType contextType) {
        DefaultFunctionContext context = ourInstances.get(contextType);
        if (context == null) {
            context = new DefaultFunctionContext(contextType);
            ourInstances.put(contextType, context);
        }
        return context;
    }
}
