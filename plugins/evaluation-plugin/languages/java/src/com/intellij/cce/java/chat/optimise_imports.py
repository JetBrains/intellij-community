import json
import os
import pathlib
import itertools
import copy
import argparse

def parse_args():
    parser = argparse.ArgumentParser(
        description='Takes all old actions.json, adds OPTIMISE_IMPORTS_STEP there and saves the result as actions-with-imports-optimise.json'
    )
    parser.add_argument('--base-path',
        required=True,
        help="Path to chat-code-generation containing project. This can be pulled from huggingface"
    )
    return parser.parse_args()

def validate_old_session(session):
    assert [action["type"] for action in session] == ["MOVE_CARET", "DELETE_RANGE", "CALL_FEATURE", "PRINT_TEXT"]

def validate_new_session(session):
    assert [action["type"] for action in session] == ["MOVE_CARET", "DELETE_RANGE", "OPTIMISE_IMPORTS", "CALL_FEATURE", "ROLLBACK"]

def process_session(session_iter, file_path):
    old_session = list(session_iter)
    validate_old_session(old_session)
    session_id = old_session[0]["sessionId"]
    
    new_session = copy.deepcopy(old_session)
    new_session.pop()
    new_session.append({"sessionId": session_id, "file": file_path, "type": "ROLLBACK"})
    new_session.insert(2, {"sessionId": session_id, "file": file_path, "type": "OPTIMISE_IMPORTS"})
    validate_new_session(new_session)
    return new_session

def process_project(project_folder, base_path):
    project_actions_filepath = os.path.join(base_path, project_folder, "actions.json")
    with open(project_actions_filepath, "r") as fin:
        project_actions = json.load(fin)

    for file_actions in project_actions:
        if file_actions["sessionsCount"] == 0:
            continue
        new_actions = []
        for sessionId, session_iter in itertools.groupby(file_actions["actions"], lambda x: x["sessionId"]):
            new_session = process_session(session_iter, file_actions["path"])
            new_actions.extend(new_session)
        file_actions["actions"] = new_actions

    output_path = os.path.join(base_path, project_folder)
    with open(output_path, "w") as fout:
        json.dump(project_actions, fout)

def main():
    args = parse_args()
    for project_folder in [
        name for name in os.listdir(args.base_path)
        if os.path.isdir(os.path.join(args.base_path, name))
    ]:
        process_project(project_folder, args.base_path)

if __name__ == "__main__":
    main()
