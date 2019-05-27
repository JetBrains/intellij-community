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
package org.snakeyaml.engine.v1.exceptions;

import java.util.Objects;
import java.util.Optional;

public class MarkedYamlEngineException extends YamlEngineException {

    private String context;
    private Optional<Mark> contextMark;
    private String problem;
    private Optional<Mark> problemMark;

    protected MarkedYamlEngineException(String context, Optional<Mark> contextMark, String problem,
                                        Optional<Mark> problemMark, Throwable cause) {
        super(context + "; " + problem + "; " + problemMark, cause);
        Objects.requireNonNull(contextMark, "contextMark must be provided");
        Objects.requireNonNull(problemMark, "problemMark must be provided");
        this.context = context;
        this.contextMark = contextMark;
        this.problem = problem;
        this.problemMark = problemMark;
    }

    protected MarkedYamlEngineException(String context, Optional<Mark> contextMark, String problem, Optional<Mark> problemMark) {
        this(context, contextMark, problem, problemMark, null);
    }

    @Override
    public String getMessage() {
        return toString();
    }

    @Override
    public String toString() {
        StringBuilder lines = new StringBuilder();
        if (context != null) {
            lines.append(context);
            lines.append("\n");
        }
        if (contextMark.isPresent()
                && (problem == null || !problemMark.isPresent()
                || contextMark.get().getName().equals(problemMark.get().getName())
                || (contextMark.get().getLine() != problemMark.get().getLine()) || (contextMark.get()
                .getColumn() != problemMark.get().getColumn()))) {
            lines.append(contextMark.get().toString());
            lines.append("\n");
        }
        if (problem != null) {
            lines.append(problem);
            lines.append("\n");
        }
        if (problemMark.isPresent()) {
            lines.append(problemMark.get().toString());
            lines.append("\n");
        }
        return lines.toString();
    }

    public String getContext() {
        return context;
    }

    public Optional<Mark> getContextMark() {
        return contextMark;
    }

    public String getProblem() {
        return problem;
    }

    public Optional<Mark> getProblemMark() {
        return problemMark;
    }
}
