
/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import java.util.logging.Level;
import java.util.logging.Logger;

public class LogStatementGuardedByLogCondition {

    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger("log");

    void guarded(Object object) {
        if (LOG.isLoggable(Level.FINE)) {
            if (true) {
                if (true) {
                    LOG.fine("really expensive logging" + object);
                }
            }
        }
    }

    void unguarded(Object object) {
        if (true) {
            if (true) {
                LOG.fine("log log log " + object);
            }
        }
    }
}
