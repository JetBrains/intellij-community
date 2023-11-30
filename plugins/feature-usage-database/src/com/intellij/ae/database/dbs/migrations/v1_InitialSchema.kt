package com.intellij.ae.database.dbs.migrations

import org.intellij.lang.annotations.Language

@Language("SQLite")
internal val MIGRATION_V1 = """
CREATE TABLE "meta" (
    version INTEGER NOT NULL 
);

CREATE TABLE "ide" (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    machine_id TEXT NOT NULL,
    ide_id TEXT NOT NULL,
    family TEXT NOT NULL
);

CREATE TABLE "counterUserActivity" (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    activity_id TEXT NOT NULL,
    ide_id TEXT NOT NULL,
    created_at TEXT NOT NULL,
    diff INTEGER NOT NULL,
    
    extra TEXT,
    
    FOREIGN KEY (ide_id) REFERENCES ide(id)
);

CREATE TABLE "timespanUserActivity" (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    activity_id TEXT NOT NULL,
    ide_id TEXT NOT NULL,
    started_at TEXT NOT NULL,
    ended_at TEXT NOT NULL,
    
    extra TEXT,
    
    FOREIGN KEY (ide_id) REFERENCES ide(id)
);

INSERT INTO meta (version) VALUES (1);
"""