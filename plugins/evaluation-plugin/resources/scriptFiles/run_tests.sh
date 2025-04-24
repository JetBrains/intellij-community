#!/bin/bash

echo "Activating virtual environment..."
VENV_PATH="./venv"
source "$VENV_PATH/bin/activate"

# Check if a specific test file or module is passed as an argument
if [ -z "$1" ]; then
    TEST_TARGET=""
else
    TEST_TARGET="$1"
fi

# Run tests (with optional specific file/module)
echo "Running tests..."
PYTHONPATH=. pytest -v "$TEST_TARGET.py" --rootdir=. --junit-xml=$TEST_TARGET-junit --cov="$2" --cov-branch --cov-report json:$TEST_TARGET-coverage
