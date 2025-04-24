#!/bin/bash

# Set the path to your virtual environment
VENV_PATH="./venv"  # Adjust this path if your virtual environment is located elsewhere

# Remove the existing virtual environment to ensure a clean setup
if [ -d "$VENV_PATH" ]; then
    echo "Removing existing virtual environment..."
    rm -rf "$VENV_PATH"
fi

# Create a new virtual environment
echo "Creating a new virtual environment..."
PYTHON_ENV=${PYTHON:-"python3"}
"$PYTHON_ENV" -m venv "$VENV_PATH"

# Activate the virtual environment
echo "Activating virtual environment..."
source "$VENV_PATH/bin/activate"


# Install dependencies from README.md
echo "Installing requirements..."
$SETUP_COMMANDS