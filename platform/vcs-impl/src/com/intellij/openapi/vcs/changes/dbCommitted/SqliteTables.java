/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.dbCommitted;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 10/8/12
 * Time: 4:27 PM
 */
public interface SqliteTables {
  String IDX_ROOT_URL = "IDX_ROOT_URL";
  String IDX_AUTHOR_NAME = "IDX_AUTHOR_NAME";
  String IDX_REVISION_DATE = "IDX_REVISION_DATE";
  String IDX_REVISION_NUMBER_INT = "IDX_REVISION_NUMBER_INT";
  String IDX_PATHS_PATH = "IDX_PATHS_PATH";

  String PREPARED_INSERT_VCS = "PREPARED_INSERT_VCS";
  String PREPARED_SELECT_VCS = "PREPARED_SELECT_VCS";
  String PREPARED_SELECT_ROOTS = "PREPARED_SELECT_ROOTS";
  String PREPARED_INSERT_ROOT = "PREPARED_INSERT_ROOT";
  String PREPARED_SELECT_MAX_REVISION = "PREPARED_SELECT_MAX_REVISION";
  String PREPARED_FILTER_KNOWN_AUTHORS = "PREPARED_FILTER_KNOWN_AUTHORS";
  String PREPARED_ADD_AUTHOR = "PREPARED_ADD_AUTHOR";
  String PREPARED_INSERT_REVISION = "PREPARED_INSERT_REVISION";
  String PREPARED_READ_PATH = "PREPARED_READ_PATH";
  String PREPARED_INSERT_PATH = "PREPARED_INSERT_PATH";
  String PREPARED_INSERT_PATH_2_REVS = "PREPARED_INSERT_PATH_2_REVS";
  String PREPARED_SELECT_REVISIONS = "PREPARED_SELECT_REVISIONS";
  String PREPARED_SELECT_PATH_DATA = "PREPARED_SELECT_PATH_DATA";
  String PREPARED_PATHS_2_REVS = "PREPARED_PATHS_2_REVS";
  String PREPARED_SELECT_PATH_DATA_BATCH = "PREPARED_SELECT_PATH_DATA_BATCH";
  String PREPARED_PATHS_2_REVS_BATCH = "PREPARED_PATHS_2_REVS_BATCH";
  String PREPARED_INSERT_INCOMING = "PREPARED_INSERT_INCOMING";
  String PREPARED_SELECT_INCOMING = "PREPARED_SELECT_INCOMING";
  String PREPARED_SELECT_MIN_REVISION = "PREPARED_SELECT_MIN_REVISION";
  String PREPARED_DATES_ONLY = "PREPARED_DATES_ONLY";
  String PREPARED_NUMBERS_SUBFOLDER = "PREPARED_NUMBERS_SUBFOLDER";
  String PREPARED_NUMBERS_ONLY = "PREPARED_NUMBERS_ONLY";
  String PREPARED_DATES_SUBFOLDER = "PREPARED_DATES_SUBFOLDER";

  abstract class BaseTable {
    public final String ID;
    public final String TABLE_NAME;
    private final String myCreateTableStatement;

    protected BaseTable(String TABLE_NAME, final String createTableStatement) {
      myCreateTableStatement = createTableStatement;
      this.ID = "ID";
      this.TABLE_NAME = TABLE_NAME;
    }

    public String getCreateTableStatement() {
      return myCreateTableStatement;
    }
  }

  @Retention(RetentionPolicy.SOURCE)
  @Target({ElementType.FIELD})
  @interface Indexed {}

  KnownVcs KNOWN_VCS = new KnownVcs();
  Root ROOT = new Root();
  Author AUTHOR = new Author();
  Revision REVISION = new Revision();
  Paths PATHS = new Paths();
  Paths2Revs PATHS_2_REVS = new Paths2Revs();
  IncomingPaths INCOMING_PATHS = new IncomingPaths();

  class KnownVcs extends BaseTable {
    public KnownVcs() {
      super("VCS", "NAME TEXT NOT NULL");
    }

    public final String NAME = "NAME";
  }


  class Root extends BaseTable {
    public Root() {
      super("ROOT", "URL TEXT NOT NULL, VCS_FK INTEGER NOT NULL REFERENCES VCS(ID)");
    }

    @Indexed
    public final String URL = "URL";
    public final String VCS_FK = "VCS_FK";
  }

  class Author extends BaseTable {
    public Author() {
      super("AUTHOR", "NAME TEXT NOT NULL");
    }

    // todo index? can have in memory..
    @Indexed
    public final String NAME = "NAME";
  }

  class Revision extends BaseTable {
    public Revision() {
      super("REVISION", "ROOT_FK INTEGER NOT NULL REFERENCES ROOT(ID), " +
                        "AUTHOR_FK INTEGER NOT NULL REFERENCES AUTHOR(ID), " +
                        "DATE INTEGER NOT NULL, " +
                        "NUMBER_STR TEXT NOT NULL, " +
                        "NUMBER_INT INTEGER NOT NULL, " +
                        "COMMENT TEXT, COUNT INTEGER NOT NULL, RAW_DATA BLOB");
    }

    public final String ROOT_FK = "ROOT_FK";
    public final String AUTHOR_FK = "AUTHOR_FK";
    @Indexed
    public final String DATE = "DATE";
    public final String NUMBER_STR = "NUMBER_STR";
    @Indexed
    public final String NUMBER_INT = "NUMBER_INT";
    public final String COMMENT = "COMMENT";
    public final String COUNT = "COUNT";
    public final String RAW_DATA = "RAW_DATA";
  }

  class Paths extends BaseTable {
    public Paths() {
      super("PATHS", "ROOT_FK INTEGER NOT NULL REFERENCES ROOT(ID), " +
                     "PATH TEXT NOT NULL");
    }

    public final String ROOT_FK = "ROOT_FK";
    @Indexed
    public final String PATH = "PATH";
  }

  class Paths2Revs extends BaseTable {
    public Paths2Revs() {
      super("PATHS_2_REVS", "PATH_FK INTEGER NOT NULL REFERENCES PATHS(ID), " +
                            "REVISION_FK INTEGER NOT NULL REFERENCES REVISION(ID), " +
                            "TYPE INTEGER NOT NULL, " +
                            "COPY_PATH_ID INTEGER REFERENCES PATHS(ID), " +
                            "VISIBLE INTEGER NOT NULL DEFAULT 1, " +
                            "DELETE_PATH_ID INTEGER REFERENCES PATHS(ID)");
    }

    public final String PATH_FK = "PATH_FK";
    public final String REVISION_FK = "REVISION_FK";
    public final String TYPE = "TYPE";
    public final String COPY_PATH_ID = "COPY_PATH_ID";
    public final String VISIBLE = "VISIBLE";
    public final String DELETE_PATH_ID = "DELETE_PATH_ID";
  }

  class IncomingPaths extends BaseTable {
    public IncomingPaths() {
      super("INCOMING_PATHS", "PR_FK INTEGER NOT NULL REFERENCES PATHS_2_REVS (ID)");
    }

    public final String PR_FK = "";
  }

  //SqlJetTypeAffinity
}
